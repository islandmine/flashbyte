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

package com.velocitypowered.proxy.protocol.util;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;
import java.util.EnumMap;
import java.util.Map;

/**
 * Rewrites the player's own entity id in raw play-state packets so that seamless (Respawn-based)
 * server switches don't desync the client and backend.
 *
 * <p>When a player switches servers without a fresh JoinGame, the client keeps the entity id it
 * received on its first server while each backend assigns its own. This rewriter swaps the player's
 * server-side entity id for the client-side one (and vice-versa) in every play packet that carries
 * it, which is the same technique BungeeCord historically used via its {@code EntityMap}.</p>
 *
 * <p>Only packets whose leading field is the entity id are handled (plus a couple of special cases),
 * which is safe because the player's entity id is unique on a given backend — no other entity can
 * share it, so a leading id that matches can only be the player.</p>
 *
 * <p>Packet ids are sourced from the Minecraft protocol per version and cover 1.21 - 1.21.11.</p>
 */
public final class EntityIdRewrite {

  private static final Map<ProtocolVersion, VersionMappings> MAPPINGS =
      new EnumMap<>(ProtocolVersion.class);

  private EntityIdRewrite() {
  }

  private static final class VersionMappings {
    // packet ids whose first field is a VarInt entity id (clientbound)
    private final boolean[] leadingVarInt = new boolean[256];
    // packet ids whose first field is a 4-byte int entity id (clientbound)
    private final boolean[] leadingInt = new boolean[256];
    // "collect item" packet: collected (varint), collector (varint) — the collector is the player
    private final int collectId;
    // serverbound "entity action" (sneak/sprint/...): leading VarInt entity id
    private final int entityActionId;

    VersionMappings(int[] leadingVarInt, int[] leadingInt, int collectId, int entityActionId) {
      for (final int id : leadingVarInt) {
        this.leadingVarInt[id] = true;
      }
      for (final int id : leadingInt) {
        this.leadingInt[id] = true;
      }
      this.collectId = collectId;
      this.entityActionId = entityActionId;
    }
  }

  private static void register(ProtocolVersion version, int[] leadingVarInt, int[] leadingInt,
      int collectId, int entityActionId) {
    MAPPINGS.put(version, new VersionMappings(leadingVarInt, leadingInt, collectId, entityActionId));
  }

  static {
    register(ProtocolVersion.MINECRAFT_1_21,
        new int[] {0x01, 0x03, 0x1a, 0x24, 0x2e, 0x2f, 0x30, 0x43, 0x48, 0x52, 0x58, 0x5a, 0x5b, 0x5f, 0x70, 0x75, 0x76},
        new int[] {0x1f, 0x59}, 0x6f, 0x25);
    register(ProtocolVersion.MINECRAFT_1_21_2,
        new int[] {0x01, 0x03, 0x1a, 0x25, 0x2f, 0x30, 0x32, 0x48, 0x4d, 0x57, 0x5d, 0x5f, 0x60, 0x65, 0x77, 0x7c, 0x7d},
        new int[] {0x1f, 0x5e}, 0x76, 0x27);
    register(ProtocolVersion.MINECRAFT_1_21_4,
        new int[] {0x01, 0x03, 0x1a, 0x25, 0x2f, 0x30, 0x32, 0x48, 0x4d, 0x57, 0x5d, 0x5f, 0x60, 0x65, 0x77, 0x7c, 0x7d},
        new int[] {0x1f, 0x5e}, 0x76, 0x28);
    register(ProtocolVersion.MINECRAFT_1_21_5,
        new int[] {0x01, 0x02, 0x19, 0x24, 0x2e, 0x2f, 0x31, 0x47, 0x4c, 0x56, 0x5c, 0x5e, 0x5f, 0x64, 0x76, 0x7c, 0x7d},
        new int[] {0x1e, 0x5d}, 0x75, 0x28);
    register(ProtocolVersion.MINECRAFT_1_21_6,
        new int[] {0x01, 0x02, 0x19, 0x24, 0x2e, 0x2f, 0x31, 0x47, 0x4c, 0x56, 0x5c, 0x5e, 0x5f, 0x64, 0x76, 0x7c, 0x7d},
        new int[] {0x1e, 0x5d}, 0x75, 0x29);
    register(ProtocolVersion.MINECRAFT_1_21_7,
        new int[] {0x01, 0x02, 0x19, 0x24, 0x2e, 0x2f, 0x31, 0x47, 0x4c, 0x56, 0x5c, 0x5e, 0x5f, 0x64, 0x76, 0x7c, 0x7d},
        new int[] {0x1e, 0x5d}, 0x75, 0x29);
    register(ProtocolVersion.MINECRAFT_1_21_9,
        new int[] {0x01, 0x02, 0x19, 0x29, 0x33, 0x34, 0x36, 0x4c, 0x51, 0x5b, 0x61, 0x63, 0x64, 0x69, 0x7b, 0x81, 0x82},
        new int[] {0x22, 0x62}, 0x7a, 0x29);
    register(ProtocolVersion.MINECRAFT_1_21_11,
        new int[] {0x01, 0x02, 0x19, 0x29, 0x33, 0x34, 0x36, 0x4c, 0x51, 0x5b, 0x61, 0x63, 0x64, 0x69, 0x7b, 0x81, 0x82},
        new int[] {0x22, 0x62}, 0x7a, 0x29);
  }

  /**
   * Rewrites a clientbound (backend → client) raw packet, swapping the backend's entity id for the
   * client's. Returns a buffer the caller owns (must be written/released); never mutates the input.
   */
  public static ByteBuf rewriteClientbound(ByteBuf buf, ProtocolVersion version,
      int serverEntityId, int clientEntityId) {
    return rewrite(buf, version, serverEntityId, clientEntityId, true);
  }

  /**
   * Rewrites a serverbound (client → backend) raw packet, swapping the client's entity id for the
   * backend's. Returns a buffer the caller owns (must be written/released); never mutates the input.
   */
  public static ByteBuf rewriteServerbound(ByteBuf buf, ProtocolVersion version,
      int clientEntityId, int serverEntityId) {
    return rewrite(buf, version, clientEntityId, serverEntityId, false);
  }

  private static ByteBuf rewrite(ByteBuf buf, ProtocolVersion version, int fromId, int toId,
      boolean clientbound) {
    if (fromId == toId) {
      // Player hasn't switched servers (or ids happen to match) — nothing to do.
      return buf.retain();
    }
    final VersionMappings m = MAPPINGS.get(version);
    if (m == null) {
      return buf.retain();
    }

    final int start = buf.readerIndex();
    final int packetId = ProtocolUtils.readVarInt(buf);

    if (clientbound) {
      if (packetId >= 0 && packetId < 256 && m.leadingVarInt[packetId]) {
        return rewriteLeadingVarInt(buf, start, packetId, fromId, toId);
      }
      if (packetId >= 0 && packetId < 256 && m.leadingInt[packetId]) {
        return rewriteLeadingInt(buf, start, packetId, fromId, toId);
      }
      if (packetId == m.collectId) {
        return rewriteCollect(buf, start, packetId, fromId, toId);
      }
    } else if (packetId == m.entityActionId) {
      return rewriteLeadingVarInt(buf, start, packetId, fromId, toId);
    }

    buf.readerIndex(start);
    return buf.retain();
  }

  private static ByteBuf rewriteLeadingVarInt(ByteBuf buf, int start, int packetId,
      int fromId, int toId) {
    final int entityId = ProtocolUtils.readVarInt(buf);
    if (entityId != fromId) {
      buf.readerIndex(start);
      return buf.retain();
    }
    final int restIdx = buf.readerIndex();
    final ByteBuf out = buf.alloc().buffer();
    ProtocolUtils.writeVarInt(out, packetId);
    ProtocolUtils.writeVarInt(out, toId);
    out.writeBytes(buf, restIdx, buf.writerIndex() - restIdx);
    buf.readerIndex(start);
    return out;
  }

  private static ByteBuf rewriteLeadingInt(ByteBuf buf, int start, int packetId,
      int fromId, int toId) {
    final int entityId = buf.readInt();
    if (entityId != fromId) {
      buf.readerIndex(start);
      return buf.retain();
    }
    final int restIdx = buf.readerIndex();
    final ByteBuf out = buf.alloc().buffer();
    ProtocolUtils.writeVarInt(out, packetId);
    out.writeInt(toId);
    out.writeBytes(buf, restIdx, buf.writerIndex() - restIdx);
    buf.readerIndex(start);
    return out;
  }

  private static ByteBuf rewriteCollect(ByteBuf buf, int start, int packetId, int fromId, int toId) {
    // Collect Item: collected entity (VarInt), collector entity (VarInt), count (VarInt).
    // The collector is the player.
    final int collected = ProtocolUtils.readVarInt(buf);
    final int collector = ProtocolUtils.readVarInt(buf);
    if (collector != fromId) {
      buf.readerIndex(start);
      return buf.retain();
    }
    final int restIdx = buf.readerIndex();
    final ByteBuf out = buf.alloc().buffer();
    ProtocolUtils.writeVarInt(out, packetId);
    ProtocolUtils.writeVarInt(out, collected);
    ProtocolUtils.writeVarInt(out, toId);
    out.writeBytes(buf, restIdx, buf.writerIndex() - restIdx);
    buf.readerIndex(start);
    return out;
  }
}
