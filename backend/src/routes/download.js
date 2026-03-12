const express = require('express');
const router = express.Router();
const { db, bucket } = require('../config/firebase');
const { s3Client } = require('../config/aws');
const { GetObjectCommand } = require('@aws-sdk/client-s3');
const { getDb } = require('../config/localdb');
const AdmZip = require('adm-zip');
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

// Download all files as a ZIP
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

        const zip = new AdmZip();

        // Helper function to stream S3 to buffer
        const streamToBuffer = async (stream) => {
            const chunks = [];
            for await (const chunk of stream) {
                chunks.push(chunk);
            }
            return Buffer.concat(chunks);
        };

        // Download files from Storage and add to zip
        for (const file of sessionData.files) {
            let buffer;

            if (file.provider === 'aws' && s3Client) {
                // Fetch from AWS S3
                const getObjectParams = {
                    Bucket: process.env.AWS_S3_BUCKET_NAME,
                    Key: file.storagePath
                };
                const response = await s3Client.send(new GetObjectCommand(getObjectParams));
                buffer = await streamToBuffer(response.Body);
            } else if (file.provider === 'firebase') {
                // Fetch from Firebase Storage
                const fileRef = bucket.file(file.storagePath);
                const [downloaded] = await fileRef.download();
                buffer = downloaded;
            } else {
                // Fetch Locally
                if (fs.existsSync(file.storagePath)) {
                    buffer = fs.readFileSync(file.storagePath);
                } else {
                    console.error(`Local file missing: ${file.storagePath}`);
                    continue;
                }
            }

            zip.addFile(file.originalName, buffer);
        }

        const zipBuffer = zip.toBuffer();

        res.set('Content-Type', 'application/zip');
        res.set('Content-Disposition', `attachment; filename=QR_Photos_${sessionId.substring(0, 6)}.zip`);
        res.set('Content-Length', zipBuffer.length);

        res.send(zipBuffer);

    } catch (error) {
        console.error('Download Zip Error:', error);
        res.status(500).send('Error generating ZIP file.');
    }
});

module.exports = router;
