package net.minecraft.util;

import java.util.AbstractSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

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
public class ClassInheritanceMultiMap<T> extends AbstractSet<T> {
	private static final Set<Class<?>> KNOWN_CLASSES = Sets.newHashSet();
	/**+
	 * EAGLER: memo of "which of this map's knownKeys isAssignableFrom this concrete class".
	 * add()/remove() used to run Class.isAssignableFrom() against EVERY key in knownKeys for EVERY
	 * entity operation, and knownKeys only ever grows for the life of a session (it accumulates
	 * every entity class that is ever queried), so an entity add/remove cost O(keys) reflective
	 * calls. Which keys match depends only on the concrete class and on this map's key set, so the
	 * answer is cached per concrete class. The cache is per-instance (two sections can legitimately
	 * have different key sets) and is dropped only when this instance learns a new key, which is the
	 * only thing that can change the answer. Bounded by the number of distinct concrete entity
	 * classes ever placed in this section; allocated lazily, so sections holding no entities cost
	 * nothing.
	 */
	private Map<Class<?>, List<Class<?>>> field_181160_b;
	private final Map<Class<?>, List<T>> map = Maps.newHashMap();
	private final Set<Class<?>> knownKeys = Sets.newIdentityHashSet();
	private final Class<T> baseClass;
	private final List<T> values = Lists.newArrayList();

	public ClassInheritanceMultiMap(Class<T> baseClassIn) {
		this.baseClass = baseClassIn;
		this.knownKeys.add(baseClassIn);
		this.map.put(baseClassIn, this.values);

		for (Class oclass : KNOWN_CLASSES) {
			this.createLookup(oclass);
		}

	}

	/**+
	 * EAGLER: the memoised assignable-key list for a concrete class in this map.
	 */
	private List<Class<?>> func_181156_a(Class<?> concrete) {
		if (this.field_181160_b == null) {
			this.field_181160_b = new HashMap<>();
		}

		List<Class<?>> cached = this.field_181160_b.get(concrete);
		if (cached == null) {
			cached = Lists.newArrayList();
			for (Class<?> oclass : this.knownKeys) {
				if (oclass.isAssignableFrom(concrete)) {
					cached.add(oclass);
				}
			}

			this.field_181160_b.put(concrete, cached);
		}

		return cached;
	}

	protected void createLookup(Class<?> clazz) {
		KNOWN_CLASSES.add(clazz);

		for (int i = 0, l = this.values.size(); i < l; ++i) {
			T object = this.values.get(i);
			if (clazz.isAssignableFrom(object.getClass())) {
				this.addForClass(object, clazz);
			}
		}

		this.knownKeys.add(clazz);
		// EAGLER: a new key can change which keys match every concrete class in this map
		if (this.field_181160_b != null) {
			this.field_181160_b.clear();
		}
	}

	protected Class<?> initializeClassLookup(Class<?> parClass1) {
		if (this.baseClass.isAssignableFrom(parClass1)) {
			if (!this.knownKeys.contains(parClass1)) {
				this.createLookup(parClass1);
			}

			return parClass1;
		} else {
			throw new IllegalArgumentException("Don\'t know how to search for " + parClass1);
		}
	}

	public boolean add(T parObject) {
		List<Class<?>> assignable = func_181156_a(parObject.getClass());
		for (int i = 0, l = assignable.size(); i < l; ++i) {
			this.addForClass(parObject, assignable.get(i));
		}

		return true;
	}

	private void addForClass(T parObject, Class<?> parClass1) {
		List list = (List) this.map.get(parClass1);
		if (list == null) {
			this.map.put(parClass1, (List<T>) Lists.newArrayList(new Object[] { parObject }));
		} else {
			list.add(parObject);
		}

	}

	public boolean remove(Object parObject) {
		Object object = parObject;
		boolean flag = false;
		List<Class<?>> assignable = func_181156_a(object.getClass());

		for (int i = 0, l = assignable.size(); i < l; ++i) {
			List list = (List) this.map.get(assignable.get(i));
			if (list != null && list.remove(object)) {
				flag = true;
			}
		}

		return flag;
	}

	public boolean contains(Object parObject) {
		return Iterators.contains(this.getByClass(parObject.getClass()).iterator(), parObject);
	}

	public <S> Iterable<S> getByClass(final Class<S> clazz) {
		return new Iterable<S>() {
			public Iterator<S> iterator() {
				List list = (List) ClassInheritanceMultiMap.this.map
						.get(ClassInheritanceMultiMap.this.initializeClassLookup(clazz));
				if (list == null) {
					return Iterators.emptyIterator();
				} else {
					Iterator iterator = list.iterator();
					return Iterators.filter(iterator, clazz);
				}
			}
		};
	}

	public Iterator<T> iterator() {
		return this.values.isEmpty() ? Iterators.emptyIterator()
				: Iterators.unmodifiableIterator(this.values.iterator());
	}

	public int size() {
		return this.values.size();
	}
}