'use strict';

let socket;
let username;
let typing = false;
let typingTimeout;
let currentPage = 0;
let loadingHistory = false;

document.addEventListener('DOMContentLoaded', () => {
    document.querySelector('#usernameForm').addEventListener('submit', connect, true);
    document.querySelector('#messageForm').addEventListener('submit', sendMessage, true);
    document.querySelector('#message').addEventListener('input', handleTyping);
});
document.addEventListener('DOMContentLoaded', () => {
    document.querySelector('#usernameForm').addEventListener('submit', connect, true);
    document.querySelector('#messageForm').addEventListener('submit', sendMessage, true);
    document.querySelector('#message').addEventListener('input', handleTyping);
    document.querySelector('#messages').addEventListener('scroll', async function () {
        if (this.scrollTop === 0 && !loadingHistory) {
            loadingHistory = true;
            await loadOldMessages();
            loadingHistory = false;
        }
    });
});

function connect(event) {
    event.preventDefault();
    username = document.querySelector('#name').value.trim();
    if (!username) return;

    document.querySelector('#username-page').classList.add('hidden');
    document.querySelector('#chat-page').classList.remove('hidden');

    socket = new WebSocket('ws://localhost:8080/ws/chat');

    socket.onopen = async () => {
        send({ type: 'JOIN', sender: username });
        await loadOldMessages();
    };

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
async function loadOldMessages() {
    if (loadingHistory) return;
    loadingHistory = true;

    try {
        const response = await fetch(`/api/messages?page=${++currentPage}&size=20`);
        const messages = await response.json();
        const messageArea = document.querySelector('#messages');

        const prevScrollHeight = messageArea.scrollHeight;

        messages.reverse().forEach(msg => {
            const message = JSON.parse(msg);
            const li = document.createElement('li');
            li.textContent = `${message.sender}: ${message.content}`;
            messageArea.prepend(li);
        });

        const newScrollHeight = messageArea.scrollHeight;
        messageArea.scrollTop = newScrollHeight - prevScrollHeight;
    } catch (e) {
        console.error("Failed to load messages:", e);
    } finally {
        loadingHistory = false;
    }
}
