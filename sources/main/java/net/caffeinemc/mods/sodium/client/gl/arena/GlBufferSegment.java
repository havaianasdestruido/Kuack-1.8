package net.caffeinemc.mods.sodium.client.gl.arena;

public final class GlBufferSegment {

	int offset;
	int length;
	boolean free;
	GlBufferSegment prev;
	GlBufferSegment next;

	public GlBufferSegment(int offset, int length, boolean free) {
		this.offset = offset;
		this.length = length;
		this.free = free;
	}

	public int getOffset() {
		return this.offset;
	}

	public int getLength() {
		return this.length;
	}

	public boolean isFree() {
		return this.free;
	}
}
