import asyncio
import json

from keypad_host.notifications import parse_notify, watch_notifications


def notify_line(app, summary, body, member="Notify"):
    return json.dumps({"type": "method_call", "member": member, "interface": "org.freedesktop.Notifications",
                       "payload": {"type": "susssasa{sv}i", "data": [app, 0, "", summary, body, [], {}, -1]}})


def test_a_pc_notification_becomes_a_message_for_the_phone():
    assert parse_notify(notify_line("Slack", "Ana", "Reunião em <b>5 min</b>")) == \
        {"type": "pc.notification", "app": "Slack", "title": "Ana", "body": "Reunião em 5 min"}


def test_other_bus_traffic_and_our_own_notices_are_ignored():
    assert parse_notify(notify_line("x", "y", "z", member="CloseNotification")) is None
    assert parse_notify('{"type":"signal","member":"NameAcquired","payload":{"data":[":1.2"]}}') is None
    assert parse_notify("not json") is None
    assert parse_notify(notify_line("omarchy-action", "Celular conectado", "s24")) is None
    assert parse_notify(notify_line("omarchy-action", "Recebido do celular", "a.pdf em Downloads")) is None


def test_long_texts_are_cut():
    n = parse_notify(notify_line("App", "T" * 500, "B" * 5000))
    assert len(n["title"]) <= 120 and len(n["body"]) <= 600


def test_notifications_are_pushed_while_they_come():
    pushed = []

    class Hub:
        async def push(self, message):
            pushed.append(message)
            return True

    async def lines():
        yield notify_line("Build", "Terminou", "ok")
        yield '{"type":"signal"}'
        yield notify_line("Mail", "Nova mensagem", "de Bia")

    async def run_once():
        task = asyncio.create_task(watch_notifications(Hub(), lines, retry=0.01))
        await asyncio.sleep(0.05)
        task.cancel()

    asyncio.run(run_once())
    assert [p["title"] for p in pushed[:2]] == ["Terminou", "Nova mensagem"]
