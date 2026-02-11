#!/usr/bin/env python3
"""
Muhabbet Test Bot — connects as Test Bot user via WebSocket and auto-replies.
Usage: python test_bot.py
"""

import asyncio
import json
import sys
import os
import time
import uuid
import random
import jwt
import websockets

# Fix Windows console encoding for emoji
if sys.platform == "win32":
    os.environ.setdefault("PYTHONIOENCODING", "utf-8")
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")

# ─── Config ──────────────────────────────────────────────
WS_URL = "wss://muhabbet.rollingcatsoftware.com/ws"
JWT_SECRET = "4CXSYHqi6f7trVF7YtKarOvOGxhHG8r9ROsnloP3Hd6LcolZCXaiUjjHsUfutu5e"
BOT_USER_ID = "52b2e3ee-bd2e-4d6d-8819-f8fc29d07e54"
BOT_DEVICE_ID = "f60b6ec8-2178-4466-b128-269dd0778fda"
BOT_NAME = "Test Bot"

# ─── Fun replies ─────────────────────────────────────────
GREETINGS = ["merhaba", "selam", "hello", "hi", "hey", "naber", "mrb", "sa", "selamlar", "hellooo", "helloooo"]
GREETING_REPLIES = [
    "Selam! Nasılsın? 😊",
    "Merhaba! Test Bot burada, hazır ve nazır! 🤖",
    "Hey! Muhabbet'e hoş geldin! 👋",
    "Selamlar! Bugün nasıl gidiyor?",
    "Heyy! Seni görmek güzel! 🎉",
]

HOW_ARE_YOU = ["nasılsın", "nasilsin", "how are you", "naber", "ne haber", "nasıl gidiyor", "n'aber"]
HOW_REPLIES = [
    "İyiyim, teşekkürler! Sen nasılsın? 😄",
    "Süper! Kodlar akıyor, bug'lar kaçıyor! 🐛💨",
    "Harika! Her mesajın anında geliyor, WS çalışıyor! ⚡",
    "Bot olarak fena değilim, 7/24 aktifim! 🤖",
]

QUESTION_REPLIES = [
    "Hmm, güzel soru! Bunu düşünmem lazım... 🤔",
    "Wow, bunu bana sordun ha? Ben sadece bir test bot'um! 😅",
    "Bu konuda Ahmet daha iyi bilir, ben sadece mesaj test ediyorum 🤖",
]

DEFAULT_REPLIES = [
    "Anladım! 👍",
    "İlginç! Devam et 😊",
    "Mesajını aldım, WS çalışıyor! ✅",
    "Harika, mesajlaşma sistemi sorunsuz! 🚀",
    "Roger that! 🫡",
    "Hmm, bunu not alıyorum 📝",
    "Süper! Başka bir şey test etmek ister misin?",
    "Test Bot olarak onaylıyorum: mesaj geldi! ✓✓",
    "Muhabbet'te her şey yolunda! 💚",
]

ECHO_TRIGGERS = ["echo", "tekrarla", "repeat"]
STATS_TRIGGERS = ["stats", "istatistik", "durum"]
HELP_TRIGGERS = ["help", "yardım", "komutlar"]


def generate_reply(content: str) -> str:
    """Pick a contextual reply based on message content."""
    lower = content.lower().strip()

    # Help command
    if any(t in lower for t in HELP_TRIGGERS):
        return (
            "🤖 Test Bot Komutları:\n"
            "• 'merhaba/selam' — selamlaşma\n"
            "• 'nasılsın' — hal hatır\n"
            "• 'echo ...' — mesajını tekrarla\n"
            "• 'stats' — bot durumu\n"
            "• 'help' — bu mesaj\n"
            "• Herhangi bir şey — rastgele cevap!"
        )

    # Echo command
    for trigger in ECHO_TRIGGERS:
        if lower.startswith(trigger):
            text = content[len(trigger):].strip()
            return f"🔊 {text}" if text else "Ne tekrarlamamı istiyorsun?"

    # Stats
    if any(t in lower for t in STATS_TRIGGERS):
        return (
            f"📊 Bot Durumu:\n"
            f"• Uptime: çalışıyor ✅\n"
            f"• WebSocket: bağlı ✅\n"
            f"• Gecikme: <100ms ⚡\n"
            f"• Mod: auto-reply 🤖"
        )

    # Greetings
    if any(g in lower for g in GREETINGS):
        return random.choice(GREETING_REPLIES)

    # How are you
    if any(h in lower for h in HOW_ARE_YOU):
        return random.choice(HOW_REPLIES)

    # Questions
    if "?" in content or lower.startswith(("ne ", "neden", "nasıl", "nerede", "kim", "what", "why", "how", "where", "who")):
        return random.choice(QUESTION_REPLIES)

    # Default
    return random.choice(DEFAULT_REPLIES)


def create_jwt() -> str:
    """Create a JWT token for the Test Bot."""
    now = int(time.time())
    payload = {
        "sub": BOT_USER_ID,
        "deviceId": BOT_DEVICE_ID,
        "iss": "muhabbet",
        "iat": now,
        "exp": now + 86400,  # 24h
    }
    return jwt.encode(payload, JWT_SECRET, algorithm="HS256")


async def run_bot():
    token = create_jwt()
    url = f"{WS_URL}?token={token}"
    message_count = 0

    print(f"🤖 {BOT_NAME} starting...")
    print(f"   Connecting to {WS_URL}")

    while True:
        try:
            async with websockets.connect(url, ping_interval=20, ping_timeout=10) as ws:
                print(f"✅ Connected! Listening for messages...")

                # Send a GoOnline message to confirm connection
                go_online = json.dumps({"type": "presence.online"})
                await ws.send(go_online)
                print("📡 Sent go_online signal")

                async for raw in ws:
                    print(f"📥 Raw frame: {str(raw)[:150]}")
                    try:
                        msg = json.loads(raw)
                    except json.JSONDecodeError:
                        print(f"⚠️  Non-JSON frame: {raw[:100]}")
                        continue

                    msg_type = msg.get("type")
                    print(f"📥 Type: {msg_type}")

                    # Handle incoming messages
                    if msg_type == "message.new":
                        sender_id = msg.get("senderId", "")
                        content = msg.get("content", "")
                        conv_id = msg.get("conversationId", "")
                        msg_id = msg.get("messageId", "")

                        # Don't reply to own messages
                        if sender_id == BOT_USER_ID:
                            continue

                        message_count += 1
                        print(f"📨 [{message_count}] From {sender_id[:8]}...: {content}")

                        # Send DELIVERED ack
                        ack = json.dumps({
                            "type": "message.ack",
                            "messageId": msg_id,
                            "conversationId": conv_id,
                            "status": "DELIVERED"
                        })
                        await ws.send(ack)

                        # Small delay to feel natural
                        await asyncio.sleep(0.5)

                        # Send READ ack
                        read_ack = json.dumps({
                            "type": "message.ack",
                            "messageId": msg_id,
                            "conversationId": conv_id,
                            "status": "READ"
                        })
                        await ws.send(read_ack)

                        # Typing indicator
                        typing = json.dumps({
                            "type": "presence.typing",
                            "conversationId": conv_id,
                            "isTyping": True
                        })
                        await ws.send(typing)

                        # Think for a bit
                        think_time = random.uniform(1.0, 2.5)
                        await asyncio.sleep(think_time)

                        # Stop typing
                        stop_typing = json.dumps({
                            "type": "presence.typing",
                            "conversationId": conv_id,
                            "isTyping": False
                        })
                        await ws.send(stop_typing)

                        # Generate and send reply
                        reply_text = generate_reply(content)
                        reply_id = str(uuid.uuid4())
                        request_id = str(uuid.uuid4())

                        reply = json.dumps({
                            "type": "message.send",
                            "requestId": request_id,
                            "messageId": reply_id,
                            "conversationId": conv_id,
                            "content": reply_text,
                            "contentType": "TEXT"
                        })
                        await ws.send(reply)
                        print(f"💬 [{message_count}] Replied: {reply_text[:60]}...")

                    elif msg_type == "ack":
                        status = msg.get("status", "")
                        if status == "ERROR":
                            print(f"⚠️  ServerAck ERROR: {msg.get('errorMessage', 'unknown')}")

                    elif msg_type == "error":
                        print(f"❌ Server error: {msg.get('message', 'unknown')}")

        except websockets.ConnectionClosed as e:
            print(f"🔌 Disconnected: {e}. Reconnecting in 3s...")
            await asyncio.sleep(3)
        except Exception as e:
            print(f"❌ Error: {e}. Reconnecting in 5s...")
            await asyncio.sleep(5)


if __name__ == "__main__":
    print("=" * 50)
    print(f"  🤖 Muhabbet Test Bot")
    print(f"  User: {BOT_NAME} ({BOT_USER_ID[:8]}...)")
    print("=" * 50)
    try:
        asyncio.run(run_bot())
    except KeyboardInterrupt:
        print("\n👋 Bot stopped. Güle güle!")
