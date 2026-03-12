const express = require('express');
const router = express.Router();
const { db, bucket } = require('../config/firebase');
const { s3Client } = require('../config/aws');
const { GetObjectCommand } = require('@aws-sdk/client-s3');
const { getDb } = require('../config/localdb');
const archiver = require('archiver');
const fs = require('fs');

// Get session info (metadata)
router.get('/info/:sessionId', async (req, res) => {
    try {
        const { sessionId } = req.params;
        let sessionData = null;
        let fromFirestore = false;

        // 1. Try Firebase Firestore
        if (db) {
            try {
                const docRef = db.collection('shared_sessions').doc(sessionId);
                const doc = await docRef.get();
                if (doc.exists) {
                    sessionData = doc.data();
                    fromFirestore = true;
                }
            } catch (err) {
                console.warn(`Firestore info error for ${sessionId}:`, err.message);
            }
        }

        // 2. Fallback to Local DB
        if (!sessionData) {
            try {
                const localDb = getDb();
                sessionData = localDb.sessions.find(s => s.sessionId === sessionId);
            } catch (err) {
                console.error('Local DB info error:', err);
            }
        }

        if (!sessionData) {
            return res.status(404).json({ error: 'Session not found or has expired.' });
        }

        // Check if expired
        const now = new Date();
        const expiresAt = fromFirestore ? sessionData.expiresAt.toDate() : new Date(sessionData.expiresAt);
        
        if (now > expiresAt) {
            return res.status(410).json({ error: 'This link has expired.' });
        }

        res.json({
            success: true,
            fileCount: sessionData.fileCount,
            totalSize: sessionData.totalSize,
            expiresAt: expiresAt
        });

    } catch (error) {
        console.error('Info Error:', error);
        res.status(500).json({ error: 'Error retrieving session information.' });
    }
});

// Download all files as a ZIP using streaming
router.get('/zip/:sessionId', async (req, res) => {
    try {
        const { sessionId } = req.params;
        let sessionData = null;
        let fromFirestore = false;

        // 1. Try Firebase Firestore
        if (db) {
            try {
                const docRef = db.collection('shared_sessions').doc(sessionId);
                const doc = await docRef.get();
                if (doc.exists) {
                    sessionData = doc.data();
                    fromFirestore = true;
                }
            } catch (err) {
                console.warn(`Firestore zip error for ${sessionId}:`, err.message);
            }
        }

        // 2. Fallback to Local DB
        if (!sessionData) {
            try {
                const localDb = getDb();
                sessionData = localDb.sessions.find(s => s.sessionId === sessionId);
            } catch (err) {
                console.error('Local DB zip error:', err);
            }
        }

        if (!sessionData) {
            return res.status(404).send('Session not found or has expired.');
        }

        // Check if expired
        const now = new Date();
        const expiresAt = fromFirestore ? sessionData.expiresAt.toDate() : new Date(sessionData.expiresAt);
        
        if (now > expiresAt) {
            return res.status(410).send('This link has expired.');
        }

        // Create a ZIP archive
        const archive = archiver('zip', {
            zlib: { level: 9 } // Sets the compression level.
        });

        // Set headers for download
        res.set('Content-Type', 'application/zip');
        res.set('Content-Disposition', `attachment; filename=QR_Photos_${sessionId.substring(0, 6)}.zip`);
        // Note: Content-Length cannot be easily set when streaming, so we omit it 
        // leading to a "chunked" transfer.

        // Catch warnings and errors from archiver
        archive.on('warning', (err) => {
            if (err.code === 'ENOENT') {
                console.warn('Archiver warning:', err);
            } else {
                throw err;
            }
        });

        archive.on('error', (err) => {
            console.error('Archiver error:', err);
            res.status(500).send({ error: err.message });
        });

        // Pipe archive data to the response
        archive.pipe(res);

        // Append files from Storage
        for (const file of sessionData.files) {
            if (file.provider === 'aws' && s3Client) {
                const getObjectParams = {
                    Bucket: process.env.AWS_S3_BUCKET_NAME,
                    Key: file.storagePath
                };
                const s3Response = await s3Client.send(new GetObjectCommand(getObjectParams));
                // Append the stream directly
                archive.append(s3Response.Body, { name: file.originalName });
            } else if (file.provider === 'firebase') {
                const fileRef = bucket.file(file.storagePath);
                // Get read stream and append
                archive.append(fileRef.createReadStream(), { name: file.originalName });
            } else {
                if (fs.existsSync(file.storagePath)) {
                    archive.append(fs.createReadStream(file.storagePath), { name: file.originalName });
                } else {
                    console.error(`Local file missing: ${file.storagePath}`);
                }
            }
        }

        // Finalize the archive
        await archive.finalize();

    } catch (error) {
        console.error('Download Zip Error:', error);
        if (!res.headersSent) {
            res.status(500).send('Error generating ZIP file.');
        }
    }
});

module.exports = router;
