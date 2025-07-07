'use strict';

let socket;
let username;

document.querySelector('#usernameForm').addEventListener('submit', connect, true);
document.querySelector('#messageForm').addEventListener('submit', sendMessage, true);

function connect(event) {
    username = document.querySelector('#name').value.trim();
    if (!username) return;

    document.querySelector('#username-page').classList.add('hidden');
    document.querySelector('#chat-page').classList.remove('hidden');

    socket = new WebSocket('ws://localhost:8080/ws/chat');

    socket.onmessage = onMessageReceived;
    socket.onopen = () => {
        send({type: 'JOIN', sender: username});
    };

    event.preventDefault();
}

function sendMessage(event) {
    const messageInput = document.querySelector('#message');
    const content = messageInput.value.trim();

    if (content && socket.readyState === WebSocket.OPEN) {
        send({type: 'CHAT', sender: username, content});
        messageInput.value = '';
    }

    event.preventDefault();
}

function send(message) {
    socket.send(JSON.stringify(message));
}

function onMessageReceived(event) {
    const message = JSON.parse(event.data);
    const messageArea = document.querySelector('#messageArea');
    const li = document.createElement('li');

    if (message.type === 'JOIN' || message.type === 'LEAVE') {
        li.classList.add('event-message');
        li.textContent = `${message.sender} ${message.type === 'JOIN' ? 'joined' : 'left'}!`;
    } else {
        li.classList.add('chat-message');
        li.textContent = `${message.sender}: ${message.content}`;
    }

    messageArea.appendChild(li);
    messageArea.scrollTop = messageArea.scrollHeight;
}
