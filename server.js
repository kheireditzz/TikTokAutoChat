const http = require('http');
const fs = require('fs');

const server = http.createServer((req, res) => {
  fs.readFile('/data/data/com.termux/files/home/TikTokAutoChat/preview.html', 'utf8', (err, data) => {
    if (err) {
      res.writeHead(500, { 'Content-Type': 'text/plain' });
      res.end('Error reading preview.html');
      return;
    }
    // No-Cache headers agar browser selalu dapat versi terbaru
    res.writeHead(200, {
      'Content-Type': 'text/html; charset=utf-8',
      'Cache-Control': 'no-store, no-cache, must-revalidate, proxy-revalidate',
      'Pragma': 'no-cache',
      'Expires': '0'
    });
    res.end(data);
  });
});

server.listen(4000, '0.0.0.0', () => {
  console.log('SERVER_LIVE_ON_PORT_4000');
});
