package com.beemovil.a2a

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages the registry of remote A2A agents.
 * Stores agent cards in SharedPreferences for persistence.
 */
object RemoteAgentRegistry {
    private const val TAG = "RemoteAgentRegistry"
    private const val PREF_NAME = "a2a_agents"
    private const val KEY_AGENTS = "registered_agents"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun getRegisteredAgents(): List<AgentCard> {
        val json = prefs?.getString(KEY_AGENTS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { AgentCard.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading agents: ${e.message}")
            emptyList()
        }
    }

    fun addAgent(card: AgentCard) {
        val agents = getRegisteredAgents().toMutableList()
        // Remove any existing with same URL
        agents.removeAll { it.url == card.url }
        agents.add(card)
        saveAgents(agents)
        Log.i(TAG, "Added agent: ${card.name} at ${card.url}")
    }

    fun removeAgent(url: String) {
        val agents = getRegisteredAgents().toMutableList()
        agents.removeAll { it.url == url }
        saveAgents(agents)
        Log.i(TAG, "Removed agent at: $url")
    }

    /**
     * Discover and auto-register an agent by URL.
     * Returns the discovered AgentCard or null.
     */
    fun discoverAndRegister(url: String): AgentCard? {
        val card = A2AClient.discoverAgent(url) ?: return null
        addAgent(card)
        return card
    }

    private fun saveAgents(agents: List<AgentCard>) {
        val arr = JSONArray().apply {
            agents.forEach { put(it.toJson()) }
        }
        prefs?.edit()?.putString(KEY_AGENTS, arr.toString())?.apply()
    }
}
