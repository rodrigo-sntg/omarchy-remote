package com.sandevsystems.omarchyremote.network

import com.sandevsystems.omarchyremote.ui.tr

import org.json.JSONObject
import java.time.Duration
import java.time.Instant

/** One window of a plan: kind "session" (5 h), "week", or "model" (a model's own weekly limit). */
data class UsageMetric(val kind: String, val label: String, val percent: Int, val resetAt: Instant?, val elapsed: Int?)

data class UsageProvider(
    val id: String, val name: String, val plan: String, val stale: Boolean, val error: String?, val resets: Int,
    val metrics: List<UsageMetric>,
    /** A Claude account other than the person's own (Accounts), "" for theirs. */
    val account: String = "",
)

/** The PC's AI plans (host/keypad_host/usage.py). [hidden]: providers with nothing to show. */
data class UsageState(val available: Boolean, val providers: List<UsageProvider>, val error: String?, val hidden: Int) {
    companion object {
        fun parse(message: JSONObject): UsageState {
            val all = message.optJSONArray("providers")?.let { list ->
                (0 until list.length()).map { i ->
                    val p = list.getJSONObject(i)
                    val metrics = p.optJSONArray("metrics")?.let { ms ->
                        (0 until ms.length()).map { j ->
                            val m = ms.getJSONObject(j)
                            UsageMetric(
                                m.optString("kind"), m.optString("label"), m.optInt("percent"),
                                m.optString("resetAt").takeIf { it.isNotEmpty() && !m.isNull("resetAt") }?.let { runCatching { Instant.parse(it) }.getOrNull() },
                                if (m.isNull("elapsed") || !m.has("elapsed")) null else m.getInt("elapsed"),
                            )
                        }
                    }.orEmpty()
                    UsageProvider(
                        p.optString("id"), p.optString("name"), p.optString("plan"), p.optBoolean("stale"),
                        if (p.isNull("error")) null else p.optString("error").ifEmpty { null }, p.optInt("resets"), metrics,
                        p.optString("account"),
                    )
                }
            }.orEmpty()
            val shown = all.filter { it.metrics.isNotEmpty() }
            return UsageState(message.optBoolean("available", true), shown,
                if (message.isNull("error")) null else message.optString("error").ifEmpty { null }, all.size - shown.size)
        }
    }
}

object UsageText {
    fun reset(at: Instant, now: Instant): String {
        val minutes = Duration.between(now, at).toMinutes()
        if (minutes <= 0) return tr("voltando agora", "resetting now")
        if (minutes < 60) return tr("volta em $minutes min", "resets in $minutes min")
        val hours = minutes / 60
        if (hours >= 24) return tr("volta em ${hours / 24}d ${hours % 24}h", "resets in ${hours / 24}d ${hours % 24}h")
        val rest = minutes % 60
        return if (rest == 0L) tr("volta em ${hours}h", "resets in ${hours}h") else tr("volta em ${hours}h %02d", "resets in ${hours}h %02d").format(rest)
    }

    /** Use against the share of the window that passed: ahead means burning faster than the time. */
    fun pace(percent: Int, elapsed: Int?): String? {
        elapsed ?: return null
        val diff = percent - elapsed
        return when {
            diff > 3 -> tr("$diff pts acima do ritmo", "$diff pts ahead of pace")
            diff < -3 -> tr("${-diff} pts abaixo do ritmo", "${-diff} pts behind pace")
            else -> tr("no ritmo", "on pace")
        }
    }

    fun label(metric: UsageMetric): String = when (metric.kind) {
        "session" -> tr("5 horas", "5 hours")
        "week" -> tr("Semana", "Week")
        else -> tr("${metric.label} · semana", "${metric.label} · week")
    }

    /** One glance: the tightest limit anywhere. */
    fun headline(state: UsageState): String? {
        val (provider, metric) = state.providers.flatMap { p -> p.metrics.map { p to it } }.maxByOrNull { it.second.percent } ?: return null
        return if (metric.percent >= 100) tr("${provider.name} esgotado", "${provider.name} used up") else "${provider.name} ${metric.percent}%"
    }
}
