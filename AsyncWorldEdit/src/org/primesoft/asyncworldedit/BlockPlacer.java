/*
 * The MIT License
 *
 * Copyright 2013 SBPrime.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.primesoft.asyncworldedit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.primesoft.asyncworldedit.worldedit.AsyncEditSession;

import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.blocks.BaseBlock;
import java.util.*;

/**
 *
 * @author SBPrime
 */
public class BlockPlacer implements Runnable {
    /**
     * Operation queue player entry
     */
    private class PlayerEntry {
        /**
         * Number of samples used in AVG count
         */
        private final int AVG_SAMPLES = 5;

        /**
         * The queue
         */
        private Queue<BlockPlacerEntry> m_queue;

        /**
         * Current block placing speed (blocks per second)
         */
        private double m_speed;

        public PlayerEntry() {
            m_queue = new ArrayDeque();
            m_speed = 0;
        }

        public Queue<BlockPlacerEntry> getQueue() {
            return m_queue;
        }

        public double getSpeed() {
            return m_speed;
        }

        public void updateSpeed(int blocks) {
            m_speed = (m_speed * (AVG_SAMPLES - 1) + blocks) / AVG_SAMPLES;
        }
    }

    /**
     * Bukkit scheduler
     */
    private BukkitScheduler m_scheduler;

    /**
     * Current scheduler task
     */
    private BukkitTask m_task;

    /**
     * Logged events queue (per player)
     */
    private HashMap<String, PlayerEntry> m_blocks;

    /**
     * All locked queues
     */
    private HashSet<String> m_lockedQueues;

    /**
     * Should block places shut down
     */
    private boolean m_shutdown;

    /**
     * Player block queue hard limit (max bloks count)
     */
    private int m_queueHardLimit;

    /**
     * Player block queue soft limit (minimum number of blocks before queue is
     * unlocked)
     */
    private int m_queueSoftLimit;

    /**
     * Global queue max size
     */
    private int m_queueMaxSize;

    /**
     * Block placing interval (in ticks)
     */
    private long m_interval;

    /**
     * Talk interval
     */
    private int m_talkInterval;

    /**
     * Run number
     */
    private int m_runNumber;

    /**
     * Initialize new instance of the block placer
     *
     * @param plugin parent
     * @param blockLogger instance block logger
     */
    public BlockPlacer(PluginMain plugin) {
        m_runNumber = 0;
        m_blocks = new HashMap<String, PlayerEntry>();
        m_lockedQueues = new HashSet<String>();
        m_scheduler = plugin.getServer().getScheduler();
        m_interval = ConfigProvider.getInterval();
        m_task = m_scheduler.runTaskTimer(plugin, this,
                m_interval, m_interval);

        m_talkInterval = ConfigProvider.getQueueTalkInterval();
        m_queueHardLimit = ConfigProvider.getQueueHardLimit();
        m_queueSoftLimit = ConfigProvider.getQueueSoftLimit();
        m_queueMaxSize = ConfigProvider.getQueueMaxSize();
    }

    /**
     * Block placer main loop
     */
    @Override
    public void run() {
        List<BlockPlacerEntry> entries = new ArrayList<BlockPlacerEntry>(ConfigProvider.getBlockCount() + ConfigProvider.getVipBlockCount());
        boolean added = false;
        synchronized (this) {
            final String[] keys = m_blocks.keySet().toArray(new String[0]);

            final HashSet<String> vips = getVips(keys);
            final String[] vipKeys = vips.toArray(new String[0]);

            final int blockCount = ConfigProvider.getBlockCount();
            final int blockCountVip = ConfigProvider.getVipBlockCount();

            added |= fetchBlocks(blockCount, keys, entries);
            added |= fetchBlocks(blockCountVip, vipKeys, entries);

            if (!added && m_shutdown) {
                stop();
            }

            final int nonVip = (keys.length != 0) ? blockCount / keys.length : 0;
            final int vip = (vipKeys.length != 0) ? blockCountVip / vipKeys.length : 0;

            m_runNumber++;
            boolean talk = false;
            if (m_runNumber > m_talkInterval) {
                m_runNumber = 0;
                talk = true;
            }

            for (Map.Entry<String, PlayerEntry> queueEntry : m_blocks.entrySet()) {
                String player = queueEntry.getKey();
                boolean isVip = vips.contains(player);

                PlayerEntry entry = queueEntry.getValue();
                entry.updateSpeed(nonVip + (isVip ? vip : 0));

                if (talk) {
                    Player p = PluginMain.getPlayer(player);
                    boolean bypass = PermissionManager.isAllowed(p, PermissionManager.Perms.QueueBypass);
                    boolean talkative = PermissionManager.isAllowed(p, PermissionManager.Perms.TalkativeQueue);

                    if (talkative) {
                        PluginMain.Say(p, "[AWE] You have " + getPlayerMessage(entry, bypass));
                    }
                }
            }
        }

        for (BlockPlacerEntry entry : entries) {
            process(entry);
        }
    }

    /**
     * Fetch the blocks that are going to by placed in this run
     *
     * @param blockCnt number of blocks to fetch
     * @param playerNames list of all players
     * @param entries destination blocks entrie
     * @return blocks fatched
     */
    private boolean fetchBlocks(final int blockCnt, final String[] playerNames,
                                List<BlockPlacerEntry> entries) {
        if (blockCnt <= 0 || playerNames == null || playerNames.length == 0) {
            return false;
        }

        int keyPos = 0;
        boolean added = playerNames.length > 0;
        for (int i = 0; i < blockCnt && added; i++) {
            added = false;

            String player = playerNames[keyPos];
            PlayerEntry playerEntry = m_blocks.get(player);
            if (playerEntry != null) {
                Queue<BlockPlacerEntry> queue = playerEntry.getQueue();
                if (!queue.isEmpty()) {
                    entries.add(queue.poll());
                    added = true;
                }
                int size = queue.size();
                if (size < m_queueSoftLimit && m_lockedQueues.contains(player)) {
                    PluginMain.Say(PluginMain.getPlayer(player), "Your block queue is unlocked. You can use WorldEdit.");
                    m_lockedQueues.remove(player);
                }
                if (size == 0) {
                    m_blocks.remove(playerNames[keyPos]);
                }
            } else if (m_lockedQueues.contains(player)) {
                PluginMain.Say(PluginMain.getPlayer(player), "Your block queue is unlocked. You can use WorldEdit.");
                m_lockedQueues.remove(player);
            }
            keyPos = (keyPos + 1) % playerNames.length;
        }
        return added;
    }

    /**
     * Queue stop command
     */
    public void queueStop() {
        m_shutdown = true;
    }

    /**
     * stop block logger
     */
    public void stop() {
        m_task.cancel();
    }

    /**
     * Add task to perform in async mode
     *
     */
    public boolean addTasks(BlockPlacerEntry entry) {
        synchronized (this) {
            AsyncEditSession editSesson = entry.getEditSession();
            String player = editSesson.getPlayer();
            PlayerEntry playerEntry;

            if (!m_blocks.containsKey(player)) {
                playerEntry = new PlayerEntry();
                m_blocks.put(player, playerEntry);
            } else {
                playerEntry = m_blocks.get(player);
            }
            Queue<BlockPlacerEntry> queue = playerEntry.getQueue();

            if (m_lockedQueues.contains(player)) {
                return false;
            }

            boolean bypass = !PermissionManager.isAllowed(PluginMain.getPlayer(player), PermissionManager.Perms.QueueBypass);
            int size = 0;
            for (Map.Entry<String, PlayerEntry> queueEntry : m_blocks.entrySet()) {
                size += queueEntry.getValue().getQueue().size();
            }

            if (m_queueMaxSize > 0 && size > m_queueMaxSize && !bypass) {
                PluginMain.Say(PluginMain.getPlayer(player), "Out of space on AWE block queue.");
                return false;
            } else {
                queue.add(entry);
                if (queue.size() >= m_queueHardLimit && bypass) {
                    m_lockedQueues.add(player);
                    PluginMain.Say(PluginMain.getPlayer(player), "Your block queue is full. Wait for items to finish drawing.");
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Remove all entries for player
     *
     * @param player
     */
    public void purge(String player) {
        synchronized (this) {
            if (m_blocks.containsKey(player)) {
                m_blocks.remove(player);
            }
            if (m_lockedQueues.contains(player)) {
                m_lockedQueues.remove(player);
            }
        }
    }

    /**
     * Remove all entries
     */
    public void purgeAll() {
        synchronized (this) {
            for (String user : getAllPlayers()) {
                purge(user);
            }
        }
    }

    /**
     * Get all players in log
     *
     * @return players list
     */
    public String[] getAllPlayers() {
        synchronized (this) {
            return m_blocks.keySet().toArray(new String[0]);
        }
    }

    /**
     * Gets the number of events for a player
     *
     * @param player player login
     * @return number of stored events
     */
    public int getPlayerEvents(String player) {
        synchronized (this) {
            if (m_blocks.containsKey(player)) {
                return m_blocks.get(player).getQueue().size();
            }
            return 0;
        }
    }

    /**
     * Gets the player message string
     *
     * @param player player login
     * @return
     */
    public String getPlayerMessage(String player) {
        PlayerEntry entry = null;
        synchronized (this) {
            if (m_blocks.containsKey(player)) {
                entry = m_blocks.get(player);
            }
        }

        boolean bypass = PermissionManager.isAllowed(PluginMain.getPlayer(player), PermissionManager.Perms.QueueBypass);
        return getPlayerMessage(entry, bypass);
    }

    /**
     * Gets the player message string
     *
     * @param player player login
     * @return
     */
    private String getPlayerMessage(PlayerEntry player, boolean bypass) {
        final String format = "%d out of %d blocks (%.2f%%) queued. Placing speed: %.2fbps, %.2fs left.";
        final String formatShort = "%d blocks queued. Placing speed: %.2fbps, %.2fs left.";

        int blocks = 0;
        double speed = 0;
        double time = 0;

        if (player != null) {
            blocks = player.getQueue().size();
            speed = player.getSpeed() / m_interval * ConfigProvider.TICKS_PER_SECOND;
        }
        if (speed > 0) {
            time = blocks / speed;
        }

        if (bypass) {
            return String.format(formatShort, blocks, speed, time);
        }

        return String.format(format, blocks, m_queueHardLimit, 100.0 * blocks / m_queueHardLimit, speed, time);
    }

    /**
     * Process logged event
     *
     * @param entry event to process
     */
    private void process(BlockPlacerEntry entry) {
        if (entry == null) {
            return;
        }

        AsyncEditSession eSession = entry.getEditSession();
        if (entry instanceof BlockPlacerBlockEntry) {
            BlockPlacerBlockEntry blockEntry = (BlockPlacerBlockEntry) entry;
            Vector location = blockEntry.getLocation();
            BaseBlock block = blockEntry.getNewBlock();
            eSession.doRawSetBlock(location, block);
        }
        if (entry instanceof BlockPlacerMaskEntry) {
            BlockPlacerMaskEntry maskEntry = (BlockPlacerMaskEntry) entry;
            eSession.doSetMask(maskEntry.getMask());
        }
    }

    /**
     * Filter player names for vip players (AWE.user.vip-queue)
     *
     * @param playerNames
     * @return
     */
    private HashSet<String> getVips(String[] playerNames) {
        if (playerNames == null || playerNames.length == 0) {
            return new HashSet<String>();
        }

        HashSet<String> result = new HashSet<String>(playerNames.length);

        for (String login : playerNames) {
            Player player = PluginMain.getPlayer(login);
            if (player == null) {
                continue;
            }

            if (PermissionManager.isAllowed(player, PermissionManager.Perms.QueueVip)
                    && !result.contains(login)) {
                result.add(login);
            }
        }

        return result;
    }
}