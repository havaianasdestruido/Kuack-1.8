package net.caffeinemc.mods.sodium.client.gl.arena;

import static net.lax1dude.eaglercraft.v1_8.internal.PlatformOpenGL.*;
import static net.lax1dude.eaglercraft.v1_8.opengl.RealOpenGLEnums.*;

import net.lax1dude.eaglercraft.v1_8.internal.IBufferGL;
import net.lax1dude.eaglercraft.v1_8.internal.buffer.ByteBuffer;
import net.lax1dude.eaglercraft.v1_8.opengl.EaglercraftGPU;

/**
 * Coalescing GPU vertex buffer arena with GPU-to-GPU buffer growth and compaction
 * via glCopyBufferSubData (WebGL 2.0 & OpenGL ES 3.0).
 */
public final class GlBufferArena {

	public interface BufferBackend {
		IBufferGL createBuffer(int byteCapacity);

		void uploadSubData(IBufferGL buffer, int byteOffset, ByteBuffer data);

		void copySubData(IBufferGL src, IBufferGL dst, int readByteOffset, int writeByteOffset, int byteLength);

		void deleteBuffer(IBufferGL buffer);
	}

	public static final BufferBackend GL_BACKEND = new BufferBackend() {
		@Override
		public IBufferGL createBuffer(int byteCapacity) {
			IBufferGL buf = _wglGenBuffers();
			EaglercraftGPU.bindGLArrayBuffer(buf);
			_wglBufferData(GL_ARRAY_BUFFER, byteCapacity, GL_DYNAMIC_DRAW);
			return buf;
		}

		@Override
		public void uploadSubData(IBufferGL buffer, int byteOffset, ByteBuffer data) {
			EaglercraftGPU.bindGLArrayBuffer(buffer);
			_wglBufferSubData(GL_ARRAY_BUFFER, byteOffset, data);
		}

		@Override
		public void copySubData(IBufferGL src, IBufferGL dst, int readByteOffset, int writeByteOffset, int byteLength) {
			if (byteLength <= 0) {
				return;
			}
			_wglBindBuffer(GL_COPY_READ_BUFFER, src);
			_wglBindBuffer(GL_COPY_WRITE_BUFFER, dst);
			_wglCopyBufferSubData(GL_COPY_READ_BUFFER, GL_COPY_WRITE_BUFFER, readByteOffset, writeByteOffset,
					byteLength);
			_wglBindBuffer(GL_COPY_READ_BUFFER, null);
			_wglBindBuffer(GL_COPY_WRITE_BUFFER, null);
		}

		@Override
		public void deleteBuffer(IBufferGL buffer) {
			if (buffer != null) {
				EaglercraftGPU.bindGLArrayBuffer(null);
				_wglDeleteBuffers(buffer);
			}
		}
	};

	private final BufferBackend backend;
	private final int strideBytes;
	private IBufferGL buffer;
	private int capacityVertices;
	private int usedVertices;
	private int activeAllocations;
	private GlBufferSegment head;
	private int bufferGeneration;

	public GlBufferArena(int initialCapacityVertices, int strideBytes) {
		this(initialCapacityVertices, strideBytes, GL_BACKEND);
	}

	public GlBufferArena(int initialCapacityVertices, int strideBytes, BufferBackend backend) {
		this.backend = backend;
		this.strideBytes = strideBytes;
		this.capacityVertices = Math.max(1024, initialCapacityVertices);
		this.usedVertices = 0;
		this.activeAllocations = 0;
		this.buffer = backend.createBuffer(this.capacityVertices * strideBytes);
		this.head = new GlBufferSegment(0, this.capacityVertices, true);
		this.bufferGeneration = 1;
	}

	public IBufferGL getBufferObject() {
		return this.buffer;
	}

	public int getBufferGeneration() {
		return this.bufferGeneration;
	}

	public int getStrideBytes() {
		return this.strideBytes;
	}

	public long getCapacityBytes() {
		return (long) this.capacityVertices * (long) this.strideBytes;
	}

	public long getUsedBytes() {
		return (long) this.usedVertices * (long) this.strideBytes;
	}

	public int getCapacityVertices() {
		return this.capacityVertices;
	}

	public int getUsedVertices() {
		return this.usedVertices;
	}

	public int getActiveAllocations() {
		return this.activeAllocations;
	}

	public boolean isEmpty() {
		return this.activeAllocations == 0;
	}

	public GlBufferSegment upload(ByteBuffer data, int vertexCount) {
		if (vertexCount <= 0) {
			return null;
		}
		GlBufferSegment seg = allocate(vertexCount);
		this.backend.uploadSubData(this.buffer, seg.offset * this.strideBytes, data);
		return seg;
	}

	public void uploadInPlace(GlBufferSegment seg, ByteBuffer data) {
		if (seg == null || seg.free) {
			throw new IllegalArgumentException("Invalid segment for in-place upload");
		}
		this.backend.uploadSubData(this.buffer, seg.offset * this.strideBytes, data);
	}

	public GlBufferSegment allocate(int vertexCount) {
		GlBufferSegment match = findBestFit(vertexCount);
		if (match == null) {
			int freeTotal = this.capacityVertices - this.usedVertices;
			if (freeTotal >= vertexCount + (this.capacityVertices >> 2)) {
				compactAndResize(this.capacityVertices);
				match = findBestFit(vertexCount);
			}
			if (match == null) {
				int minRequired = this.usedVertices + vertexCount;
				int grown = this.capacityVertices + (this.capacityVertices >> 1);
				if (grown < minRequired) {
					grown = minRequired + (minRequired >> 2);
				}
				// Align to 256 vertices
				grown = (grown + 255) & ~255;
				compactAndResize(grown);
				match = findBestFit(vertexCount);
			}
		}

		if (match.length > vertexCount) {
			GlBufferSegment remainder = new GlBufferSegment(match.offset + vertexCount, match.length - vertexCount,
					true);
			remainder.prev = match;
			remainder.next = match.next;
			if (match.next != null) {
				match.next.prev = remainder;
			}
			match.next = remainder;
			match.length = vertexCount;
		}

		match.free = false;
		this.usedVertices += vertexCount;
		++this.activeAllocations;
		return match;
	}

	private GlBufferSegment findBestFit(int vertexCount) {
		GlBufferSegment best = null;
		for (GlBufferSegment cur = this.head; cur != null; cur = cur.next) {
			if (cur.free && cur.length >= vertexCount) {
				if (cur.length == vertexCount) {
					return cur;
				}
				if (best == null || cur.length < best.length) {
					best = cur;
				}
			}
		}
		return best;
	}

	public void free(GlBufferSegment seg) {
		if (seg == null || seg.free) {
			return;
		}
		seg.free = true;
		this.usedVertices -= seg.length;
		--this.activeAllocations;

		// Coalesce with next free segment
		if (seg.next != null && seg.next.free) {
			GlBufferSegment nxt = seg.next;
			seg.length += nxt.length;
			seg.next = nxt.next;
			if (seg.next != null) {
				seg.next.prev = seg;
			}
			nxt.prev = null;
			nxt.next = null;
		}

		// Coalesce with prev free segment
		if (seg.prev != null && seg.prev.free) {
			GlBufferSegment prv = seg.prev;
			prv.length += seg.length;
			prv.next = seg.next;
			if (prv.next != null) {
				prv.next.prev = prv;
			}
			seg.prev = null;
			seg.next = null;
		}
	}

	/**
	 * Compacts all live segments contiguously into a new GPU buffer of newCapacityVertices,
	 * copying live bytes GPU-to-GPU via glCopyBufferSubData and updating live segment offsets in-place.
	 */
	public void compactAndResize(int newCapacityVertices) {
		if (newCapacityVertices < this.usedVertices) {
			newCapacityVertices = this.usedVertices;
		}
		IBufferGL newBuf = this.backend.createBuffer(newCapacityVertices * this.strideBytes);
		int writeVertexCursor = 0;
		GlBufferSegment newHead = null;
		GlBufferSegment tail = null;

		for (GlBufferSegment cur = this.head; cur != null;) {
			GlBufferSegment next = cur.next;
			if (!cur.free) {
				this.backend.copySubData(this.buffer, newBuf, cur.offset * this.strideBytes,
						writeVertexCursor * this.strideBytes, cur.length * this.strideBytes);
				cur.offset = writeVertexCursor;
				writeVertexCursor += cur.length;
				cur.prev = tail;
				cur.next = null;
				if (tail != null) {
					tail.next = cur;
				} else {
					newHead = cur;
				}
				tail = cur;
			} else {
				cur.prev = null;
				cur.next = null;
			}
			cur = next;
		}

		int remainingFree = newCapacityVertices - writeVertexCursor;
		if (remainingFree > 0) {
			GlBufferSegment trailingFree = new GlBufferSegment(writeVertexCursor, remainingFree, true);
			trailingFree.prev = tail;
			if (tail != null) {
				tail.next = trailingFree;
			} else {
				newHead = trailingFree;
			}
		}

		this.backend.deleteBuffer(this.buffer);
		this.buffer = newBuf;
		this.capacityVertices = newCapacityVertices;
		this.head = newHead;
		++this.bufferGeneration;
	}

	public void destroy() {
		if (this.buffer != null) {
			this.backend.deleteBuffer(this.buffer);
			this.buffer = null;
		}
		this.head = null;
		this.usedVertices = 0;
		this.activeAllocations = 0;
	}
}
