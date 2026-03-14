require('dotenv').config();
const express = require('express');
const cors = require('cors');
const path = require('path');
const cronService = require('./services/cron');

const app = express();
const PORT = process.env.PORT || 3000;

// Middleware
app.use(cors());
app.use(express.json());
app.use(express.urlencoded({ extended: true }));
app.use(express.static(path.join(__dirname, '../public')));

// Routes
const uploadRoute = require('./routes/upload');
const downloadRoute = require('./routes/download');

app.use('/api/upload', uploadRoute);
app.use('/api/download', downloadRoute);

// Health check / Keep-alive route
app.get('/ping', (req, res) => {
    res.send('pong');
});

// Serve the download page for any view links
// e.g. /view/:fileId
app.get('/view/:fileId', (req, res) => {
    res.sendFile(path.join(__dirname, '../public/download.html'));
});

// Start cleanup cron job (runs every hour to delete expired files)
cronService.start();

app.listen(PORT, () => {
    console.log(`Server running on port ${PORT}`);
});
