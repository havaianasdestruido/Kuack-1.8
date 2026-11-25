package net.lax1dude.eaglercraft.v1_8.buildtools.task.init;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.FileUtils;

import net.lax1dude.eaglercraft.v1_8.buildtools.EaglerBuildTools;

/**
 * Regenerates the checked in, patched Minecraft source tree from the build
 * inputs vendored in this repository. The generated files are the exact same
 * files the client compiler uses: decompiled 1.8.8 sources, formatted with the
 * MCP Eclipse formatter, with every patch in patches/minecraft and
 * patches/resources applied on top.
 *
 * Usage: java -cp "buildtools/BuildTools.jar:buildtools/deps/*"
 *        net.lax1dude.eaglercraft.v1_8.buildtools.task.init.RegenerateVendored
 *        [repository root] [temporary directory]
 */
public class RegenerateVendored {

	public static void main(String[] args) throws Throwable {
		if(args.length != 2) {
			System.err.println("Usage: RegenerateVendored <repository root> <temporary directory>");
			System.exit(2);
			return;
		}

		File repository = new File(args[0]).getCanonicalFile();
		File temporary = new File(args[1]).getAbsoluteFile();
		EaglerBuildTools.repositoryRoot = repository;

		File vendor = new File(repository, "vendor/minecraft");
		File assets = new File(vendor, "assets");
		if(System.getProperty("eaglercraft.assetsDirectory") == null) {
			System.setProperty("eaglercraft.assetsDirectory", assets.getAbsolutePath());
		}
		if(System.getProperty("eaglercraft.ffmpeg") != null) {
			FFMPEG.foundFFMPEG = System.getProperty("eaglercraft.ffmpeg");
		}

		File mcpData = new File(temporary, "ModCoderPack");
		File generated = new File(temporary, "MinecraftSrc");
		FileUtils.deleteDirectory(mcpData);
		FileUtils.deleteDirectory(generated);
		if(!mcpData.mkdirs() || !generated.mkdirs()) {
			throw new RuntimeException("Could not create temporary directories in " + temporary);
		}

		if(!InitMCP.initTask(new File(vendor, "mcp918.zip"), mcpData)) {
			throw new RuntimeException("Could not initialize MCP from vendor/minecraft/mcp918.zip");
		}
		if(!DecompileMinecraft.decompileMinecraft(mcpData, new File(vendor, "1.8.8.jar"), generated,
				new File(vendor, "1.8.json"), false)) {
			throw new RuntimeException("Could not decompile and patch vendor/minecraft/1.8.8.jar");
		}

		File sourceDir = new File(repository, "sources/minecraft/java");
		File resourceDir = new File(repository, "sources/minecraft/assets");
		File languageDir = new File(repository, "sources/minecraft/lang");
		FileUtils.deleteDirectory(sourceDir);
		FileUtils.deleteDirectory(resourceDir);
		FileUtils.deleteDirectory(languageDir);

		int javaFiles = extractJava(new File(generated, "minecraft_src_javadoc.jar"), sourceDir);
		int resourceFiles = extractEntries(new File(generated, "minecraft_res_patch.jar"), resourceDir);
		int languageFiles = extractEntries(new File(generated, "minecraft_languages.zip"), languageDir);

		System.out.println();
		System.out.println("Wrote " + javaFiles + " patched Java files to " + sourceDir);
		System.out.println("Wrote " + resourceFiles + " patched resource files to " + resourceDir);
		System.out.println("Wrote " + languageFiles + " language files to " + languageDir);

		if(javaFiles < 1400 || languageFiles < 50) {
			throw new RuntimeException("Unexpectedly few generated files, refusing to update the checked in source");
		}
	}

	private static int extractJava(File archive, File destination) throws Exception {
		int count = 0;
		try(ZipInputStream in = new ZipInputStream(new java.io.FileInputStream(archive))) {
			ZipEntry entry;
			while((entry = in.getNextEntry()) != null) {
				if(entry.isDirectory() || !entry.getName().endsWith(".java")) {
					continue;
				}
				writeEntry(in, entry, destination);
				++count;
			}
		}
		return count;
	}

	private static int extractEntries(File archive, File destination) throws Exception {
		int count = 0;
		try(ZipInputStream in = new ZipInputStream(new java.io.FileInputStream(archive))) {
			ZipEntry entry;
			while((entry = in.getNextEntry()) != null) {
				if(entry.isDirectory() || entry.getName().startsWith("META-INF/")) {
					continue;
				}
				writeEntry(in, entry, destination);
				++count;
			}
		}
		return count;
	}

	private static void writeEntry(InputStream in, ZipEntry entry, File destination) throws Exception {
		File file = new File(destination, entry.getName());
		File parent = file.getParentFile();
		if(!parent.exists() && !parent.mkdirs()) {
			throw new RuntimeException("Could not create directory " + parent);
		}
		try(OutputStream out = new FileOutputStream(file)) {
			byte[] buffer = new byte[16384];
			int read;
			while((read = in.read(buffer)) != -1) {
				out.write(buffer, 0, read);
			}
		}
	}
}
