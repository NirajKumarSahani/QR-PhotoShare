const express = require('express');
const router = express.Router();
const multer = require('multer');
const { v4: uuidv4 } = require('uuid');
const QRCode = require('qrcode');
const { db, bucket } = require('../config/firebase');
const { s3Client } = require('../config/aws');
const { PutObjectCommand } = require('@aws-sdk/client-s3');
const { getDb, saveDb } = require('../config/localdb');
const mime = require('mime-types');
const fs = require('fs');
const path = require('path');

// Setup multer for local temporary storage
// Max 100MB per upload (combined size of all photos)
const uploadDir = path.join(__dirname, '../../uploads');
if (!fs.existsSync(uploadDir)) {
    fs.mkdirSync(uploadDir, { recursive: true });
}

const storage = multer.diskStorage({
    destination: function (req, file, cb) {
        cb(null, uploadDir)
    },
    filename: function (req, file, cb) {
        cb(null, uuidv4() + '-' + file.originalname)
    }
});

const upload = multer({
    storage: storage,
    limits: { fileSize: 100 * 1024 * 1024 } // 100MB max per file, but really we want total 100MB.
});

router.post('/', upload.array('photos', 50), async (req, res) => {
    try {
        if (!req.files || req.files.length === 0) {
            return res.status(400).json({ error: 'No files uploaded.' });
        }

        const sessionId = uuidv4();
        let totalSize = 0;

        // Check total size limit (100MB)
        for (const file of req.files) {
            totalSize += file.size;
        }

        if (totalSize > 100 * 1024 * 1024) {
            // Delete uploaded temp files
            req.files.forEach(f => fs.unlinkSync(f.path));
            return res.status(400).json({ error: 'Total upload size exceeds 100MB limit.' });
        }

        // Determine available providers
        const providers = [];
        if (bucket) {
            providers.push('firebase');
        }
        if (s3Client && process.env.AWS_S3_BUCKET_NAME) {
            providers.push('aws');
        }
        providers.push('local'); // Always available

        console.log(`Available storage providers: ${providers.join(', ')}`);

        // Upload each file to either Firebase Storage, AWS S3, or Local Disk in parallel
        const localUploadDir = path.join(uploadDir, sessionId);
        const uploadPromises = req.files.map(async (file, index) => {
            let storageProvider = providers[index % providers.length];
            console.log(`Processing ${file.filename} via ${storageProvider}...`);
            
            let destFileName = `uploads/${sessionId}/${file.filename}`;
            let fileStoragePath = destFileName; // default for cloud

            const uploadLocal = () => {
                if (!fs.existsSync(localUploadDir)) {
                    fs.mkdirSync(localUploadDir, { recursive: true });
                }
                const newPath = path.join(localUploadDir, file.filename);
                fs.copyFileSync(file.path, newPath);
                return newPath;
            };

            try {
                if (storageProvider === 'aws') {
                    const fileStream = fs.createReadStream(file.path);
                    const uploadParams = {
                        Bucket: process.env.AWS_S3_BUCKET_NAME,
                        Key: destFileName,
                        Body: fileStream,
                        ContentType: file.mimetype
                    };
                    await s3Client.send(new PutObjectCommand(uploadParams));
                } else if (storageProvider === 'firebase') {
                    await bucket.upload(file.path, {
                        destination: destFileName,
                        metadata: {
                            contentType: file.mimetype,
                        }
                    });
                } else {
                    fileStoragePath = uploadLocal();
                }
            } catch (uploadError) {
                console.warn(`Upload to ${storageProvider} failed for ${file.filename}, falling back to local:`, uploadError.message);
                storageProvider = 'local';
                fileStoragePath = uploadLocal();
            }

            // Delete original temp file after successful processing (local copy already exists if fallback happened)
            if (fs.existsSync(file.path)) {
                fs.unlinkSync(file.path);
            }

            return {
                originalName: file.originalname,
                storagePath: fileStoragePath,
                size: file.size,
                mimetype: file.mimetype,
                provider: storageProvider
            };
        });

        const uploadedFiles = await Promise.all(uploadPromises);

        // Create DB record
        const now = new Date();
        const expiresAt = new Date(now.getTime() + 12 * 60 * 60 * 1000); // 12 hours from now

        const sessionData = {
            sessionId: sessionId,
            createdAt: now,
            expiresAt: expiresAt,
            files: uploadedFiles,
            totalSize: totalSize,
            fileCount: uploadedFiles.length
        };

        // Save to Firebase Firestore (if available)
        if (db) {
            try {
                const docRef = db.collection('shared_sessions').doc(sessionId);
                await docRef.set(sessionData);
                console.log(`Session ${sessionId} saved to Firestore`);
            } catch (fsError) {
                console.error('Firestore Save Error:', fsError);
                // Continue even if Firestore fails, as we have local DB
            }
        }

        // ALSO save to Local Database (backup)
        try {
            const localDb = getDb();
            localDb.sessions.push({
                ...sessionData,
                createdAt: sessionData.createdAt.toISOString(),
                expiresAt: sessionData.expiresAt.toISOString()
            });
            saveDb(localDb);
            console.log(`Session ${sessionId} saved to Local DB`);
        } catch (dbError) {
            console.error('Local DB Save Error:', dbError);
            if (!db) throw new Error('Both Firestore and Local DB failed to save session data.');
        }

        // Generate download URL and QR code
        const host = req.get('host');
        // Determine protocol based on environment or proxy
        const protocol = req.headers['x-forwarded-proto'] || req.protocol;
        const downloadUrl = `${protocol}://${host}/view/${sessionId}`;

        const qrCodeDataUrl = await QRCode.toDataURL(downloadUrl);

        res.status(200).json({
            success: true,
            sessionId: sessionId,
            downloadUrl: downloadUrl,
            qrCode: qrCodeDataUrl,
            expiresAt: expiresAt
        });

    } catch (error) {
        console.error('Upload Error:', error);
        // Clean up partial temp files if error occurs
        if (req.files) {
            req.files.forEach(f => {
                if (fs.existsSync(f.path)) {
                    try { fs.unlinkSync(f.path); } catch(e) {}
                }
            });
        }
        res.status(500).json({ 
            success: false,
            error: error.message || 'Internal server error during upload.' 
        });
    }
});

module.exports = router;
