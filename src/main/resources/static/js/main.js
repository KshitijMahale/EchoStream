'use strict';

let socket;
let username;
let typing = false;
let typingTimeout;

document.addEventListener('DOMContentLoaded', () => {
    document.querySelector('#usernameForm').addEventListener('submit', connect, true);
    document.querySelector('#messageForm').addEventListener('submit', sendMessage, true);
    document.querySelector('#message').addEventListener('input', handleTyping);
});

function connect(event) {
    event.preventDefault();
    username = document.querySelector('#name').value.trim();
    if (!username) return;

    document.querySelector('#username-page').classList.add('hidden');
    document.querySelector('#chat-page').classList.remove('hidden');

    socket = new WebSocket('ws://localhost:8080/ws/chat');

    socket.onopen = () => send({ type: 'JOIN', sender: username });
    socket.onmessage = onMessageReceived;
}

function sendMessage(event) {
    event.preventDefault();
    const messageInput = document.querySelector('#message');
    const content = messageInput.value.trim();

    if (content && socket.readyState === WebSocket.OPEN) {
        send({ type: 'CHAT', sender: username, content });
        messageInput.value = '';
        stopTyping();
    }
}

function handleTyping() {
    if (!typing) {
        typing = true;
        send({ type: 'TYPING', sender: username });
    }
    clearTimeout(typingTimeout);
    typingTimeout = setTimeout(stopTyping, 500);
}

function stopTyping() {
    if (typing) {
        typing = false;
        send({ type: 'STOP_TYPING', sender: username });
    }
}

function showTyping(sender) {
    if (document.querySelector(`#typing-${sender}`)) return;

    const li = document.createElement('li');
    li.id = `typing-${sender}`;
    li.classList.add('typing-indicator');
    li.textContent = `${sender} is typing...`;

    document.querySelector('#messages').appendChild(li);
}

function removeTyping(sender) {
    const typingElement = document.querySelector(`#typing-${sender}`);
    if (typingElement) typingElement.remove();
}

function send(message) {
    socket.send(JSON.stringify(message));
}

function onMessageReceived(event) {
    const message = JSON.parse(event.data);
    const messageArea = document.querySelector('#messages');

    if (message.type === 'TYPING') {
        showTyping(message.sender);
        return;
    }
    if (message.type === 'STOP_TYPING') {
        removeTyping(message.sender);
        return;
    }

    const li = document.createElement('li');
    li.classList.add(message.type === 'JOIN' || message.type === 'LEAVE' ? 'event-message' : 'chat-message');
    li.textContent = message.type === 'JOIN'
        ? `${message.sender} joined!`
        : message.type === 'LEAVE'
        ? `${message.sender} left!`
        : `${message.sender}: ${message.content}`;

    messageArea.appendChild(li);
    messageArea.scrollTop = messageArea.scrollHeight;
}
