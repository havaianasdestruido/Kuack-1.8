package net.minecraft.world.gen;

import net.lax1dude.eaglercraft.v1_8.EaglercraftRandom;

/**+
 * This portion of EaglercraftX contains deobfuscated Minecraft 1.8 source code.
 * 
 * Minecraft 1.8.8 bytecode is (c) 2015 Mojang AB. "Do not distribute!"
 * Mod Coder Pack v9.18 deobfuscation configs are (c) Copyright by the MCP Team
 * 
 * EaglercraftX 1.8 patch files (c) 2022-2024 lax1dude, ayunami2000. All Rights Reserved.
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
public class NoiseGeneratorSimplex {
	private static int[][] grad3 = new int[][] { { 1, 1, 0 }, { -1, 1, 0 }, { 1, -1, 0 }, { -1, -1, 0 },
			{ 1, 0, 1 }, { -1, 0, 1 }, { 1, 0, -1 }, { -1, 0, -1 }, { 0, 1, 1 }, { 0, -1, 1 }, { 0, 1, -1 },
			{ 0, -1, -1 } };
	public static final double SQRT_3 = Math.sqrt(3.0D);
	private int[] p;
	public double xo;
	public double yo;
	public double zo;
	private static final double F2 = 0.5D * (SQRT_3 - 1.0D);
	private static final double G2 = (3.0D - SQRT_3) / 6.0D;

	public NoiseGeneratorSimplex() {
		this(new EaglercraftRandom());
	}

	public NoiseGeneratorSimplex(EaglercraftRandom parRandom) {
		this.p = new int[512];
		this.xo = parRandom.nextDouble() * 256.0D;
		this.yo = parRandom.nextDouble() * 256.0D;
		this.zo = parRandom.nextDouble() * 256.0D;

		for (int i = 0; i < 256; this.p[i] = i++) {
			;
		}

		for (int l = 0; l < 256; ++l) {
			int j = parRandom.nextInt(256 - l) + l;
			int k = this.p[l];
			this.p[l] = this.p[j];
			this.p[j] = k;
			this.p[l + 256] = this.p[l];
		}

	}

	private static int fastFloor(double parDouble1) {
		return parDouble1 > 0.0D ? (int) parDouble1 : (int) parDouble1 - 1;
	}

	private static double dot(int[] parArrayOfInt, double parDouble1, double parDouble2) {
		return (double) parArrayOfInt[0] * parDouble1 + (double) parArrayOfInt[1] * parDouble2;
	}

	public double getValue(double parDouble1, double parDouble2) {
		double d3 = 0.5D * (SQRT_3 - 1.0D);
		double d4 = (parDouble1 + parDouble2) * d3;
		int i = fastFloor(parDouble1 + d4);
		int j = fastFloor(parDouble2 + d4);
		double d5 = (3.0D - SQRT_3) / 6.0D;
		double d6 = (double) (i + j) * d5;
		double d7 = (double) i - d6;
		double d8 = (double) j - d6;
		double d9 = parDouble1 - d7;
		double d10 = parDouble2 - d8;
		byte b0;
		byte b1;
		if (d9 > d10) {
			b0 = 1;
			b1 = 0;
		} else {
			b0 = 0;
			b1 = 1;
		}

		double d11 = d9 - (double) b0 + d5;
		double d12 = d10 - (double) b1 + d5;
		double d13 = d9 - 1.0D + 2.0D * d5;
		double d14 = d10 - 1.0D + 2.0D * d5;
		int k = i & 255;
		int l = j & 255;
		int i1 = this.p[k + this.p[l]] % 12;
		int j1 = this.p[k + b0 + this.p[l + b1]] % 12;
		int k1 = this.p[k + 1 + this.p[l + 1]] % 12;
		double d15 = 0.5D - d9 * d9 - d10 * d10;
		double d0;
		if (d15 < 0.0D) {
			d0 = 0.0D;
		} else {
			d15 = d15 * d15;
			d0 = d15 * d15 * dot(grad3[i1], d9, d10);
		}

		double d16 = 0.5D - d11 * d11 - d12 * d12;
		double d1;
		if (d16 < 0.0D) {
			d1 = 0.0D;
		} else {
			d16 = d16 * d16;
			d1 = d16 * d16 * dot(grad3[j1], d11, d12);
		}

		double d17 = 0.5D - d13 * d13 - d14 * d14;
		double d2;
		if (d17 < 0.0D) {
			d2 = 0.0D;
		} else {
			d17 = d17 * d17;
			d2 = d17 * d17 * dot(grad3[k1], d13, d14);
		}

		return 70.0D * (d0 + d1 + d2);
	}

	public void add(double[] parArrayOfDouble, double parDouble1, double parDouble2, int parInt1, int parInt2,
			double parDouble3, double parDouble4, double parDouble5) {
		int i = 0;

		for (int j = 0; j < parInt2; ++j) {
			double d0 = (parDouble2 + (double) j) * parDouble4 + this.yo;

			for (int k = 0; k < parInt1; ++k) {
				double d1 = (parDouble1 + (double) k) * parDouble3 + this.xo;
				double d5 = (d1 + d0) * F2;
				int l = fastFloor(d1 + d5);
				int i1 = fastFloor(d0 + d5);
				double d6 = (double) (l + i1) * G2;
				double d7 = (double) l - d6;
				double d8 = (double) i1 - d6;
				double d9 = d1 - d7;
				double d10 = d0 - d8;
				byte b0;
				byte b1;
				if (d9 > d10) {
					b0 = 1;
					b1 = 0;
				} else {
					b0 = 0;
					b1 = 1;
				}

				double d11 = d9 - (double) b0 + G2;
				double d12 = d10 - (double) b1 + G2;
				double d13 = d9 - 1.0D + 2.0D * G2;
				double d14 = d10 - 1.0D + 2.0D * G2;
				int j1 = l & 255;
				int k1 = i1 & 255;
				int l1 = this.p[j1 + this.p[k1]] % 12;
				int i2 = this.p[j1 + b0 + this.p[k1 + b1]] % 12;
				int j2 = this.p[j1 + 1 + this.p[k1 + 1]] % 12;
				double d15 = 0.5D - d9 * d9 - d10 * d10;
				double d2;
				if (d15 < 0.0D) {
					d2 = 0.0D;
				} else {
					d15 = d15 * d15;
					d2 = d15 * d15 * dot(grad3[l1], d9, d10);
				}

				double d16 = 0.5D - d11 * d11 - d12 * d12;
				double d3;
				if (d16 < 0.0D) {
					d3 = 0.0D;
				} else {
					d16 = d16 * d16;
					d3 = d16 * d16 * dot(grad3[i2], d11, d12);
				}

				double d17 = 0.5D - d13 * d13 - d14 * d14;
				double d4;
				if (d17 < 0.0D) {
					d4 = 0.0D;
				} else {
					d17 = d17 * d17;
					d4 = d17 * d17 * dot(grad3[j2], d13, d14);
				}

				int k2 = i++;
				parArrayOfDouble[k2] += 70.0D * (d2 + d3 + d4) * parDouble5;
			}
		}

	}
}