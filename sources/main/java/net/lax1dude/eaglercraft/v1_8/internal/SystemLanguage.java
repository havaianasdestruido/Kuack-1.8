package net.lax1dude.eaglercraft.v1_8.internal;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.json.JSONObject;

import net.lax1dude.eaglercraft.v1_8.EagRuntime;

/**
 * Copyright (c) 2022-2024 lax1dude. All Rights Reserved.
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

/**
 * Matches the user's system/browser language (a BCP 47 tag such as "pt-BR")
 * against the languages that are actually shipped with the game, so the
 * default client language can follow the operating system/browser settings
 * instead of always being "en_US".
 * 
 * A language only ever matches when it is listed in the "language" section of
 * pack.mcmeta (the same list the language selection screen is built from) and
 * its .lang file is present (in the EPK assets, or at localesURI on the
 * website). When nothing matches, {@link #DEFAULT_LOCALE} is returned; when
 * pack.mcmeta cannot be read yet, null is returned so callers can retry
 * instead of caching a guess.
 */
public class SystemLanguage {

	/**
	 * The language used when the system language cannot be detected, is not
	 * supported, or its language file is not available.
	 */
	public static final String DEFAULT_LOCALE = "en_US";

	/**
	 * Legacy ISO 639 language codes that browsers may still report, mapped to
	 * the codes used by the shipped language files.
	 */
	private static final Map<String, String> LANG_ALIASES = new HashMap();

	static {
		LANG_ALIASES.put("iw", "he");
		LANG_ALIASES.put("in", "id");
		LANG_ALIASES.put("nb", "no");
		LANG_ALIASES.put("mo", "ro");
		LANG_ALIASES.put("tl", "fil");
		LANG_ALIASES.put("sh", "sr");
	}

	/**
	 * Preferred variant for every base language that ships with more than one
	 * region, so that a bare tag like "en" or an unsupported region like
	 * "en-IN" resolves to a well-defined language file instead of depending
	 * on iteration order.
	 */
	private static final Map<String, String> PREFERRED_VARIANTS = new HashMap();

	static {
		PREFERRED_VARIANTS.put("en", "en_US");
		PREFERRED_VARIANTS.put("es", "es_ES");
		PREFERRED_VARIANTS.put("fr", "fr_FR");
		PREFERRED_VARIANTS.put("pt", "pt_BR");
		PREFERRED_VARIANTS.put("zh", "zh_CN");
	}

	/**
	 * Resolve the system language tags, in preference order (as reported by
	 * navigator.languages), to a language shipped with the game. The first
	 * tag that matches an available language file wins; if none do, the
	 * default locale is returned. This method never throws.
	 * 
	 * @param tags BCP 47 language tags, highest preference first
	 * @return a language code listed in pack.mcmeta with a present .lang
	 *         file, {@link #DEFAULT_LOCALE} if no tag matches, or null when
	 *         pack.mcmeta is not available yet (the caller should retry
	 *         later rather than caching a guess)
	 */
	public static String detect(String... tags) {
		Set<String> available = null;
		try {
			available = loadAvailableLanguages();
		} catch (Throwable t) {
			// detecting the system language must never prevent the game from booting
		}
		if (available == null || available.isEmpty()) {
			return null;
		}
		if (tags != null) {
			for (int i = 0; i < tags.length; ++i) {
				List<String> candidates;
				try {
					candidates = candidates(tags[i], available);
				} catch (Throwable t) {
					continue;
				}
				for (int j = 0; j < candidates.size(); ++j) {
					String candidate = candidates.get(j);
					try {
						if (isLanguageFilePresent(candidate)) {
							return candidate;
						}
					} catch (Throwable t) {
						// treat an unreadable language file as unavailable
					}
				}
			}
		}
		return DEFAULT_LOCALE;
	}

	/**
	 * Build the ordered list of language codes to try for a single BCP 47
	 * tag, in this order: exact region match, Chinese script/region remap,
	 * the preferred variant of the base language, then every other shipped
	 * variant of the base language (sorted, so the result is deterministic).
	 * Only codes contained in the available set are ever returned, and the
	 * list contains no duplicates.
	 * 
	 * @param tag a BCP 47 language tag such as "pt-BR" or "zh-Hant-HK"
	 * @param available the language codes to match against
	 * @return an ordered, duplicate-free list of matching language codes
	 */
	public static List<String> candidates(String tag, Set<String> available) {
		List<String> ret = new ArrayList();
		if (tag == null || available == null || available.isEmpty()) {
			return ret;
		}
		String normalized = tag.replace('_', '-').trim();
		if (normalized.isEmpty()) {
			return ret;
		}
		String[] parts = normalized.split("-");
		if (parts.length == 0 || parts[0].length() == 0) {
			return ret;
		}
		String lang = parts[0].toLowerCase(Locale.ROOT);
		String alias = LANG_ALIASES.get(lang);
		if (alias != null) {
			lang = alias;
		}
		String region = null;
		String script = null;
		for (int i = 1; i < parts.length; ++i) {
			String part = parts[i];
			if (part.length() == 2 && isAsciiLetters(part)) {
				if (region == null) {
					region = part.toUpperCase(Locale.ROOT);
				}
			} else if (part.length() == 4 && isAsciiLetters(part)) {
				if (script == null) {
					script = part.toLowerCase(Locale.ROOT);
				}
			}
		}
		if (region != null) {
			addCandidate(ret, lang + "_" + region, available);
		}
		if ("zh".equals(lang)) {
			// simplified Chinese always maps to zh_CN, traditional to zh_TW,
			// including regions with no dedicated file (SG, HK, MO, ...)
			addCandidate(ret, ("hant".equals(script) || "HK".equals(region) || "MO".equals(region))
					? "zh_TW" : "zh_CN", available);
		}
		String preferred = PREFERRED_VARIANTS.get(lang);
		if (preferred != null) {
			addCandidate(ret, preferred, available);
		}
		String prefix = lang + "_";
		List<String> variants = new ArrayList();
		for (String code : available) {
			if (code.startsWith(prefix)) {
				variants.add(code);
			}
		}
		if (variants.size() > 1) {
			Collections.sort(variants);
		}
		for (int i = 0; i < variants.size(); ++i) {
			addCandidate(ret, variants.get(i), available);
		}
		return ret;
	}

	private static void addCandidate(List<String> list, String code, Set<String> available) {
		if (available.contains(code) && !list.contains(code)) {
			list.add(code);
		}
	}

	private static boolean isAsciiLetters(String string) {
		for (int i = 0; i < string.length(); ++i) {
			char c = string.charAt(i);
			if ((c < 'a' || c > 'z') && (c < 'A' || c > 'Z')) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Read the supported language codes from the "language" section of
	 * pack.mcmeta, which is the same list the language selection screen is
	 * built from.
	 * 
	 * @return the set of supported language codes, or null if pack.mcmeta is
	 *         not available (for example before the EPK assets have loaded)
	 */
	private static Set<String> loadAvailableLanguages() {
		byte[] bytes = EagRuntime.getResourceBytes("pack.mcmeta");
		if (bytes == null || bytes.length == 0) {
			return null;
		}
		JSONObject meta = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
		JSONObject languages = meta.optJSONObject("language");
		if (languages == null) {
			return null;
		}
		Set<String> set = new HashSet();
		Iterator<String> itr = languages.keys();
		while (itr.hasNext()) {
			String code = itr.next();
			if (code != null && code.length() > 0) {
				set.add(code);
			}
		}
		return set;
	}

	/**
	 * Check that the .lang file for a language code is actually present,
	 * either bundled in the EPK assets (en_US and the languages.epk of the
	 * international offline client) or hosted at localesURI on the website.
	 * This keeps clients that only ship en_US.lang from selecting a language
	 * whose file would be missing.
	 */
	private static boolean isLanguageFilePresent(String code) {
		byte[] bytes = EagRuntime.getResourceBytes("assets/minecraft/lang/" + code + ".lang");
		return bytes != null && bytes.length > 0;
	}

}
