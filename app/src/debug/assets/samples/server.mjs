import http from 'node:http';

const server = http.createServer((_request, response) => {
  response.end(JSON.stringify({ status: 'ok' }));
});

server.listen(3000);
