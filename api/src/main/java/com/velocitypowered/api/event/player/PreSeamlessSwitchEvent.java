/*
 * Copyright (C) 2018-2023 Velocity Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocitypowered.api.event.player;

import com.google.common.base.Preconditions;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Fired during a seamless (screenless) server switch, right before the proxy drops the player's
 * connection to the server they are leaving. The client never receives a JoinGame or Respawn for
 * such a switch and keeps its world, so anything the leaving server put into the client (entities,
 * chunks, scoreboards, effects) survives unless somebody removes it. Handlers that need the leaving
 * server to clean up may return an asynchronous {@link com.velocitypowered.api.event.EventTask};
 * the proxy waits for it, bounded by a short timeout, before disconnecting the leaving server.
 */
public final class PreSeamlessSwitchEvent {

  private final Player player;
  private final RegisteredServer leavingServer;
  private final @Nullable RegisteredServer targetServer;

  /**
   * Creates the event.
   *
   * @param player        the switching player
   * @param leavingServer the server the player is leaving
   * @param targetServer  the server the player is switching to, if known
   */
  public PreSeamlessSwitchEvent(Player player, RegisteredServer leavingServer,
      @Nullable RegisteredServer targetServer) {
    this.player = Preconditions.checkNotNull(player, "player");
    this.leavingServer = Preconditions.checkNotNull(leavingServer, "leavingServer");
    this.targetServer = targetServer;
  }

  public Player getPlayer() {
    return player;
  }

  public RegisteredServer getLeavingServer() {
    return leavingServer;
  }

  public @Nullable RegisteredServer getTargetServer() {
    return targetServer;
  }

  @Override
  public String toString() {
    return "PreSeamlessSwitchEvent{"
        + "player=" + player
        + ", leavingServer=" + leavingServer
        + ", targetServer=" + targetServer
        + '}';
  }
}
