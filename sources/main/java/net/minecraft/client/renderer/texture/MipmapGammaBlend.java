package net.minecraft.client.renderer.texture;

/**+
 * This portion of EaglercraftX contains deobfuscated Minecraft 1.8 source code.
 * 
 * Minecraft 1.8.8 bytecode is (c) 2015 Mojang AB. "Do not distribute!"
 * Mod Coder Pack v9.18 deobfuscation configs are (c) Copyright by the MCP Team
 * 
 * EaglercraftX 1.8 patch files are (c) 2022-2024 lax1dude. All Rights Reserved.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * 
 */

/*
 * The mipmap box filter used by TextureUtil.generateMipmapData, split out of TextureUtil so the
 * arithmetic can be exercised without bringing up the GL texture stack (TextureUtil's static
 * initialiser allocates a 16 MiB direct buffer and a DynamicTexture, so it cannot even be loaded on
 * a plain JVM).
 *
 * What it does: four neighbouring 8-bit-per-channel texels are averaged. Vanilla does the average in
 * linear light when the sprite has transparency (the gamma-correct box filter, the "flag" branch) and
 * in a slightly cheaper form otherwise; both branches decode every channel byte with
 *   (float) Math.pow((float) (v & 255) / 255.0F, 2.2)
 * and encode the result with Math.pow(avg, 1 / 2.2).
 *
 * Why a table: the decode step has exactly 256 distinct inputs (one per byte value) and runs up to 20
 * times per output texel (4 channels x 4 neighbours in the gamma branch, 4 channels in the linear
 * branch). That makes it an ideal integer-indexed lookup: a 256-entry float table costs 1 KiB and
 * turns a 43 ns libm call (V8 measurement, see perfbench/js/bench_math_lut.js) into a ~1 ns array
 * read. Nothing can go stale - the mapping is a pure function of the byte value - and it is built
 * once in the static initializer (256 Math.pow calls, ~10 us) with no per-call allocation.
 *
 * Exactness: each entry is produced with the identical expression the call sites used, including the
 * int->float conversion, the float division by 255.0F, the double root and the narrowing cast back
 * to float, so a lookup is bit-for-bit identical to the old computation and the generated mipmaps are
 * unchanged. Only the encode step (Math.pow(avg, 1/2.2)) still calls libm: its input is a continuous
 * average of table values, so it has no small domain to tabulate, and quantising it would perturb
 * dark texels by several LSBs.
 *
 * Verified by perfbench.VerifyGammaLut (exhaustive over all 256 decode inputs plus a differential
 * test of the whole filter against a verbatim copy of the vanilla code, 8,295,168 checks) and
 * measured by perfbench.TextureGammaBench (4.6-5.1x). See perfbench/FINDINGS.md #11.
 */
public final class MipmapGammaBlend {
	/** sRGB-ish decode: GAMMA_2_2[v] == (float) Math.pow((float) v / 255.0F, 2.2). */
	private static final float[] GAMMA_2_2 = new float[256];

	/** Scratch copy of the four texels, kept as a field so the loop does not touch an array parameter. */
	private static final int[] mipmapBuffer = new int[4];

	private MipmapGammaBlend() {
	}

	/**
	 * Blends four ARGB texels into one. {@code gammaCorrect} selects the linear-light average used
	 * when the sprite contains transparency (identical to vanilla's {@code blendColors}).
	 */
	public static int blend(int parInt1, int parInt2, int parInt3, int parInt4, boolean gammaCorrect) {
		if (!gammaCorrect) {
			int i1 = blendComponent(parInt1, parInt2, parInt3, parInt4, 24);
			int j1 = blendComponent(parInt1, parInt2, parInt3, parInt4, 16);
			int k1 = blendComponent(parInt1, parInt2, parInt3, parInt4, 8);
			int l1 = blendComponent(parInt1, parInt2, parInt3, parInt4, 0);
			return i1 << 24 | j1 << 16 | k1 << 8 | l1;
		} else {
			mipmapBuffer[0] = parInt1;
			mipmapBuffer[1] = parInt2;
			mipmapBuffer[2] = parInt3;
			mipmapBuffer[3] = parInt4;
			float f = 0.0F;
			float f1 = 0.0F;
			float f2 = 0.0F;
			float f3 = 0.0F;

			for (int i = 0; i < 4; ++i) {
				if (mipmapBuffer[i] >> 24 != 0) {
					f += GAMMA_2_2[mipmapBuffer[i] >> 24 & 255];
					f1 += GAMMA_2_2[mipmapBuffer[i] >> 16 & 255];
					f2 += GAMMA_2_2[mipmapBuffer[i] >> 8 & 255];
					f3 += GAMMA_2_2[mipmapBuffer[i] >> 0 & 255];
				}
			}

			f = f / 4.0F;
			f1 = f1 / 4.0F;
			f2 = f2 / 4.0F;
			f3 = f3 / 4.0F;
			int i2 = (int) (Math.pow((double) f, 0.45454545454545453D) * 255.0D);
			int j = (int) (Math.pow((double) f1, 0.45454545454545453D) * 255.0D);
			int k = (int) (Math.pow((double) f2, 0.45454545454545453D) * 255.0D);
			int l = (int) (Math.pow((double) f3, 0.45454545454545453D) * 255.0D);
			if (i2 < 96) {
				i2 = 0;
			}

			return i2 << 24 | j << 16 | k << 8 | l;
		}
	}

	/** Blends one channel (selected by {@code shift}) of four ARGB texels, in linear light. */
	public static int blendComponent(int parInt1, int parInt2, int parInt3, int parInt4, int shift) {
		float f = GAMMA_2_2[parInt1 >> shift & 255];
		float f1 = GAMMA_2_2[parInt2 >> shift & 255];
		float f2 = GAMMA_2_2[parInt3 >> shift & 255];
		float f3 = GAMMA_2_2[parInt4 >> shift & 255];
		float f4 = (float) Math.pow((double) (f + f1 + f2 + f3) * 0.25D, 0.45454545454545453D);
		return (int) ((double) f4 * 255.0D);
	}

	/** The raw decode table, exposed for the exhaustive equivalence check in perfbench. */
	public static float gamma22(int byteValue) {
		return GAMMA_2_2[byteValue & 255];
	}

	static {
		for (int i = 0; i < 256; ++i) {
			GAMMA_2_2[i] = (float) Math.pow((double) ((float) i / 255.0F), 2.2D);
		}
	}
}
