import asyncio

from keypad_host.notifications import parse_notify
from keypad_host.phone_notices import PhoneNotices, notify_args


class Hub:
    def __init__(self):
        self.pushed = []

    async def push(self, m):
        self.pushed.append(m)
        return True


def test_a_phone_notification_is_shown_with_reply_only_when_it_can_be_answered():
    args = notify_args({"key": "k1", "app": "WhatsApp", "title": "Ana", "text": "chego às 8", "reply": True})
    assert args[:4] == ["notify-send", "--app-name=omarchy-remote", "--print-id", "--wait"]
    assert "--action=default=Responder" in args   # Omarchy shows no buttons: a click is the default action
    assert args[-2:] == ["WhatsApp · Ana", "chego às 8"]
    plain = notify_args({"key": "k2", "app": "Banco", "title": "Pix recebido", "text": "R$ 10", "reply": False})
    assert "--action=default=Dispensar" in plain and not any("Responder" in a for a in plain)


def test_answering_on_the_pc_sends_the_reply_to_the_phone():
    hub = Hub()
    asked = []

    async def show(args):
        yield "42"          # the notification's id
        yield "default"     # the person clicked it (Responder)

    async def ask(prompt):
        asked.append(prompt)
        return "combinado"

    async def main():
        notices = PhoneNotices(hub, show=show, ask=ask, close=lambda i: None)
        await notices.posted({"key": "k1", "app": "WhatsApp", "title": "Ana", "text": "chego às 8", "reply": True})
        await asyncio.sleep(0)
        await notices.wait()

    asyncio.run(main())
    assert asked == ["Responder a Ana"]
    assert hub.pushed == [{"type": "phone.reply", "key": "k1", "text": "combinado"}]


def test_dismiss_on_the_pc_dismisses_on_the_phone_and_a_removal_closes_it_on_the_pc():
    hub = Hub()
    closed = []

    async def show(args):
        yield "7"
        yield "default"

    async def main():
        notices = PhoneNotices(hub, show=show, ask=None, close=closed.append)
        await notices.posted({"key": "k9", "app": "Gmail", "title": "Fatura", "text": "", "reply": False})
        await notices.wait()
        assert hub.pushed == [{"type": "phone.dismiss", "key": "k9"}]

        async def lingering(args):
            yield "8"
            await asyncio.sleep(10)   # stays until closed

        notices.show = lingering
        await notices.posted({"key": "k10", "app": "Gmail", "title": "Outra", "text": "", "reply": False})
        await asyncio.sleep(0.01)
        await notices.removed("k10")
        assert closed == [8]

    asyncio.run(main())


def test_an_empty_reply_sends_nothing():
    hub = Hub()

    async def show(args):
        yield "1"
        yield "default"

    async def ask(prompt):
        return ""

    async def main():
        notices = PhoneNotices(hub, show=show, ask=ask, close=lambda i: None)
        await notices.posted({"key": "k", "app": "A", "title": "B", "text": "", "reply": True})
        await notices.wait()

    asyncio.run(main())
    assert hub.pushed == []


def test_the_phones_notifications_shown_on_the_pc_do_not_go_back_to_the_phone():
    line = '{"type":"method_call","member":"Notify","payload":{"data":["omarchy-remote",0,"","WhatsApp · Ana","chego",[],{},-1]}}'
    assert parse_notify(line) is None


def test_the_phone_notification_messages_are_validated():
    import json
    import pytest
    from keypad_host.protocol import ProtocolError, parse

    def frame(t, p):
        return json.dumps({"v": 1, "sessionId": "s", "seq": 1, "type": t, "payload": p})

    m = parse(frame("phone.notification", {"key": "0|com.whatsapp|1|null|10", "app": "WhatsApp", "title": "Ana", "text": "oi", "reply": True}))
    assert m.payload == {"key": "0|com.whatsapp|1|null|10", "app": "WhatsApp", "title": "Ana", "text": "oi", "reply": True}
    assert parse(frame("phone.notification.removed", {"key": "k"})).payload == {"key": "k"}
    with pytest.raises(ProtocolError):
        parse(frame("phone.notification", {"key": "", "app": "A", "title": "", "text": "", "reply": False}))
    with pytest.raises(ProtocolError):
        parse(frame("phone.notification", {"key": "k", "app": "A" * 200, "title": "", "text": "", "reply": False}))
