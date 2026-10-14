package dev.xplaytime.db

import java.nio.ByteBuffer
import java.util.UUID

object UuidBinary {
    fun toBytes(uuid: UUID): ByteArray {
        val buffer = ByteBuffer.wrap(ByteArray(16))
        buffer.putLong(uuid.mostSignificantBits)
        buffer.putLong(uuid.leastSignificantBits)
        return buffer.array()
    }

    fun fromBytes(bytes: ByteArray): UUID {
        val buffer = ByteBuffer.wrap(bytes)
        return UUID(buffer.long, buffer.long)
    }
}
