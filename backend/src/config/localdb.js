const fs = require('fs');
const path = require('path');

const DB_FILE = path.join(__dirname, '../../database.json');

// Initialize local DB file
if (!fs.existsSync(DB_FILE)) {
    fs.writeFileSync(DB_FILE, JSON.stringify({ sessions: [] }, null, 2));
}

function getDb() {
    try {
        const data = fs.readFileSync(DB_FILE, 'utf8');
        return JSON.parse(data);
    } catch (e) {
        return { sessions: [] };
    }
}

function saveDb(data) {
    fs.writeFileSync(DB_FILE, JSON.stringify(data, null, 2));
}

module.exports = { getDb, saveDb };
