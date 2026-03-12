const cron = require('node-cron');
const { db, bucket } = require('../config/firebase');
const { s3Client } = require('../config/aws');
const { DeleteObjectCommand } = require('@aws-sdk/client-s3');
const { getDb, saveDb } = require('../config/localdb');
const fs = require('fs');
const path = require('path');

function startCronJob() {
    // Run every hour
    cron.schedule('0 * * * *', async () => {
        console.log('Running cleanup cron job...');
        try {
            const now = new Date();
            
            // 1. Cleanup Firebase / Cloud Records
            let firebaseDeletedCount = 0;
            try {
                const snapshot = await db.collection('shared_sessions')
                    .where('expiresAt', '<', now)
                    .get();

                if (!snapshot.empty) {
                    for (const doc of snapshot.docs) {
                        const data = doc.data();

                        // Delete files from storage
                        for (const file of data.files) {
                            try {
                                if (file.provider === 'aws' && s3Client) {
                                    const deleteParams = { Bucket: process.env.AWS_S3_BUCKET_NAME, Key: file.storagePath };
                                    await s3Client.send(new DeleteObjectCommand(deleteParams));
                                } else if (file.provider === 'firebase') {
                                    await bucket.file(file.storagePath).delete();
                                } else if (file.provider === 'local') {
                                    if (fs.existsSync(file.storagePath)) fs.unlinkSync(file.storagePath);
                                }
                            } catch (err) { }
                        }

                        try { await bucket.deleteFiles({ prefix: `uploads/${data.sessionId}/` }); } catch (e) { }

                        // Delete local session folder just in case
                        try {
                            const sessionDir = path.join(__dirname, '../../uploads', data.sessionId);
                            if (fs.existsSync(sessionDir)) fs.rmdirSync(sessionDir);
                        } catch(e) {}

                        // Delete document from firestore
                        await db.collection('shared_sessions').doc(doc.id).delete();
                        firebaseDeletedCount++;
                    }
                }
            } catch (e) { console.error("Firebase cleanup error:", e); }


            // 2. Cleanup Local JSON Database (Fallback/Backup)
            let localDeletedCount = 0;
            try {
                const localDb = getDb();
                const validSessions = [];
                for (const session of localDb.sessions) {
                    const expiresAt = new Date(session.expiresAt);
                    if (now > expiresAt) {
                        localDeletedCount++;
                    } else {
                        validSessions.push(session);
                    }
                }
                if (localDeletedCount > 0) {
                    localDb.sessions = validSessions;
                    saveDb(localDb);
                }
            } catch(e) { console.error("Local DB cleanup error:", e); }

            console.log(`Cleanup complete. Deleted Firebase records: ${firebaseDeletedCount}, Local records: ${localDeletedCount}`);

        } catch (error) {
            console.error('Error in cleanup cron job:', error);
        }
    });
}

module.exports = { start: startCronJob };
