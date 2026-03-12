const { S3Client } = require('@aws-sdk/client-s3');

// AWS S3 Configuration
// Initialize only if AWS credentials exist
let s3Client = null;

if (process.env.AWS_REGION && process.env.AWS_ACCESS_KEY_ID && process.env.AWS_SECRET_ACCESS_KEY) {
    try {
        const s3Config = {
            region: process.env.AWS_REGION || 'auto', // Cloudflare R2 uses 'auto'
            credentials: {
                accessKeyId: process.env.AWS_ACCESS_KEY_ID,
                secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY
            }
        };

        if (process.env.AWS_ENDPOINT) {
            s3Config.endpoint = process.env.AWS_ENDPOINT;
        }

        s3Client = new S3Client(s3Config);
        console.log("AWS S3 initialized successfully");
    } catch (error) {
        console.error("AWS S3 initialization error:", error);
    }
} else {
    console.warn("AWS credentials not found. S3 operations will fallback to Firebase or fail if explicitly required.");
}

module.exports = { s3Client };
