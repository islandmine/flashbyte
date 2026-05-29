/*
 * Copyright (C) 2024 Flashbyte Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.velocitypowered.proxy.protocol.packet;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;

/**
 * The clientbound "Game Event" packet (historically "Change Game State"). Carries a single-byte
 * event id and a float value.
 *
 * <p>The proxy sends this with {@link #START_WAITING_FOR_LEVEL_CHUNKS} during a seamless server
 * switch on 1.20.3+: after the JoinGame + Respawn the client otherwise sits on the "Loading
 * terrain" screen until this event arrives (or a 30s timeout elapses). Sending it immediately lets
 * the new world's chunks stream straight in.</p>
 */
public class GameEventPacket implements MinecraftPacket {

  /** Game event id 13: "Start waiting for level chunks" (1.20.3+). */
  public static final short START_WAITING_FOR_LEVEL_CHUNKS = 13;

  private short event;
  private float value;

  public GameEventPacket() {
  }

  public GameEventPacket(short event, float value) {
    this.event = event;
    this.value = value;
  }

  public short getEvent() {
    return event;
  }

  public void setEvent(short event) {
    this.event = event;
  }

  public float getValue() {
    return value;
  }

  public void setValue(float value) {
    this.value = value;
  }

  @Override
  public void decode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion version) {
    this.event = buf.readUnsignedByte();
    this.value = buf.readFloat();
  }

  @Override
  public void encode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion version) {
    buf.writeByte(event);
    buf.writeFloat(value);
  }

  @Override
  public boolean handle(MinecraftSessionHandler handler) {
    return handler.handle(this);
  }

  @Override
  public String toString() {
    return "GameEventPacket{event=" + event + ", value=" + value + '}';
  }
}
