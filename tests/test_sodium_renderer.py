"""
Offline sanity checks for the built-in SodiumMC renderer's pure logic.

The Java sources under test are not compiled by this file, instead the exact
algorithms are mirrored here so the invariants the renderer relies on can be
verified without a JDK or a browser. Each check corresponds to a rule in
SodiumConfig/SodiumChunkRenderer.
"""
import unittest


# --- mirror of SodiumConfig ------------------------------------------------

MAX_UPLOADS = [0, 2, 4, 8]
RESORT_INTERVAL = [0.0, 100.0, 0.0, 0.0]
CULL_PENDING = [False, True, True, False]
LEVEL_COUNT = 4


def clamp_level(level):
    return 0 if level < 0 else (LEVEL_COUNT - 1 if level >= LEVEL_COUNT else level)


def cycle_level(level, direction):
    ret = (clamp_level(level) + direction) % LEVEL_COUNT
    if ret < 0:
        ret += LEVEL_COUNT
    return ret


def is_enabled(level):
    return clamp_level(level) != 0


def max_uploads(level):
    i = MAX_UPLOADS[clamp_level(level)]
    return MAX_UPLOADS[2] if i == 0 else i


def resort_interval(level):
    return RESORT_INTERVAL[clamp_level(level)]


def cull_pending(level):
    return CULL_PENDING[clamp_level(level)]


# --- mirror of SodiumChunkRenderer ----------------------------------------


def dist_sq(pos, x, y, z):
    dx = pos[0] + 8.0 - x
    dy = pos[1] + 8.0 - y
    dz = pos[2] + 8.0 - z
    return dx * dx + dy * dy + dz * dz


def insertion_sort(chunks, x, y, z, translucent):
    chunks = list(chunks)
    for i in range(1, len(chunks)):
        chunk = chunks[i]
        d = dist_sq(chunk, x, y, z)
        j = i - 1
        while j >= 0 and dist_sq(chunks[j], x, y, z) > d:
            chunks[j + 1] = chunks[j]
            j -= 1
        chunks[j + 1] = chunk
    if translucent:
        chunks.reverse()
    return chunks


def rebuild_candidates(chunk_x, chunk_y, chunk_z, distance, view, get_chunk):
    """Mirror of rebuildCandidates(), the set is a pure function of its args."""
    r = distance + 1
    max_dist = (distance << 4) + 16.0
    max_dist_sq = max_dist * max_dist
    out = []
    for dx in range(-r, r + 1):
        for dz in range(-r, r + 1):
            if dx * dx + dz * dz > r * r:
                continue
            for dy in range(-16, 17):
                cy = chunk_y + dy
                if cy < 0 or cy > 15:
                    continue
                pos = ((chunk_x + dx) << 4, cy << 4, (chunk_z + dz) << 4)
                chunk = get_chunk(pos)
                if chunk is None:
                    continue
                cx = pos[0] + 8.0 - view[0]
                cyy = pos[1] + 8.0 - view[1]
                cz = pos[2] + 8.0 - view[2]
                if cx * cx + cyy * cyy + cz * cz > max_dist_sq:
                    continue
                out.append(pos)
    return out


class SodiumConfigTest(unittest.TestCase):
    def test_levels_cycle_and_wrap(self):
        self.assertEqual(cycle_level(2, 1), 3)
        self.assertEqual(cycle_level(3, 1), 0, 'wraps back to OFF')
        self.assertEqual(cycle_level(0, 1), 1)
        self.assertEqual(cycle_level(0, -1), 3, 'negative direction wraps')
        self.assertEqual(cycle_level(1, -1), 0)

    def test_clamp_rejects_saved_garbage(self):
        # settings are loaded from browser storage and may be corrupt
        self.assertEqual(clamp_level(-99), 0)
        self.assertEqual(clamp_level(99), LEVEL_COUNT - 1)

    def test_off_is_disabled_others_enabled(self):
        self.assertFalse(is_enabled(0))
        for level in (1, 2, 3):
            self.assertTrue(is_enabled(level))

    def test_upload_budget_is_always_nonzero_when_enabled(self):
        # the vanilla dispatcher drains every queued chunk per frame, a sodium
        # level that allowed zero uploads would freeze the world
        for level in (1, 2, 3):
            self.assertGreater(max_uploads(level), 0)
        self.assertLessEqual(max_uploads(1), max_uploads(3), 'fast is the strictest')

    def test_only_the_strictest_levels_defer_offscreen_updates(self):
        self.assertFalse(cull_pending(0))
        self.assertTrue(cull_pending(1))
        self.assertTrue(cull_pending(2))
        self.assertFalse(cull_pending(3))


class SodiumSortTest(unittest.TestCase):
    def test_front_to_back(self):
        chunks = [(0, 0, 0), (32, 0, 0), (16, 0, 0), (48, 0, 0)]
        order = insertion_sort(chunks, 8.0, 8.0, 8.0, translucent=False)
        self.assertEqual(order, [(0, 0, 0), (16, 0, 0), (32, 0, 0), (48, 0, 0)])

    def test_translucent_pass_is_exact_reverse(self):
        chunks = [(48, 0, 0), (0, 0, 0), (32, 0, 0), (16, 0, 0)]
        opaque = insertion_sort(chunks, 8.0, 8.0, 8.0, translucent=False)
        translucent = insertion_sort(chunks, 8.0, 8.0, 8.0, translucent=True)
        self.assertEqual(translucent, list(reversed(opaque)),
                         'translucent blending requires back to front')

    def test_sort_is_stable_for_equal_distances(self):
        # two sections centered around the camera are equidistant, neither may
        # be dropped or duplicated by the swap based reverse
        chunks = [(0, 0, 0), (16, 0, 0)]
        order = insertion_sort(chunks, 16.0, 8.0, 8.0, translucent=True)
        self.assertEqual(sorted(order), sorted(chunks))
        self.assertEqual(len(order), 2)

    def test_already_sorted_list_is_untouched(self):
        chunks = [(0, 0, 0), (16, 0, 0), (32, 0, 0)]
        self.assertEqual(insertion_sort(chunks, 8.0, 8.0, 8.0, False), chunks)


class SodiumCandidateTest(unittest.TestCase):
    def setUp(self):
        self.built = []

        def get_chunk(pos):
            self.built.append(pos)
            return pos

        self.get_chunk = get_chunk

    def test_respects_render_distance(self):
        chunks = rebuild_candidates(0, 8, 0, 4, (8.0, 64.0, 8.0), self.get_chunk)
        self.assertTrue(chunks)
        limit = ((4 + 1) << 4) + 16.0
        for pos in chunks:
            d = ((pos[0] + 8 - 8.0) ** 2 + (pos[1] + 8 - 64.0) ** 2 + (pos[2] + 8 - 8.0) ** 2) ** 0.5
            self.assertLessEqual(d, limit)

    def test_vertical_index_is_clamped_to_the_world(self):
        chunks = rebuild_candidates(0, 8, 0, 1, (8.0, 64.0, 8.0), self.get_chunk)
        for pos in chunks:
            self.assertGreaterEqual(pos[1], 0)
            self.assertLessEqual(pos[1], 15 * 16)

    def test_bigger_render_distance_is_a_superset(self):
        small = set(rebuild_candidates(0, 8, 0, 2, (8.0, 64.0, 8.0), self.get_chunk))
        big = set(rebuild_candidates(0, 8, 0, 6, (8.0, 64.0, 8.0), self.get_chunk))
        self.assertTrue(small.issubset(big))

    def test_returns_no_duplicates(self):
        chunks = rebuild_candidates(3, 8, -2, 5, (40.0, 130.0, -30.0), self.get_chunk)
        self.assertEqual(len(chunks), len(set(chunks)),
                         'a duplicated chunk would be rendered twice per frame')


def should_rescan(dirty, prev_chunk, chunk, prev_distance, distance):
    """Mirror of the cache invalidation test in getRenderChunks()."""
    return dirty or chunk != prev_chunk or distance != prev_distance


class SodiumCacheTest(unittest.TestCase):
    def test_no_rescan_while_inside_the_same_chunk(self):
        # walking around inside one 16x16 column must not rebuild anything
        self.assertFalse(should_rescan(False, (0, 4, 0), (0, 4, 0), 8, 8))
        self.assertFalse(should_rescan(False, (0, 4, 0), (0, 4, 0), 8, 8))

    def test_rescan_when_crossing_a_chunk_border(self):
        self.assertTrue(should_rescan(False, (0, 4, 0), (1, 4, 0), 8, 8))
        self.assertTrue(should_rescan(False, (0, 4, 0), (0, 5, 0), 8, 8))
        self.assertTrue(should_rescan(False, (0, 4, 0), (0, 4, -1), 8, 8))

    def test_rescan_on_render_distance_change(self):
        self.assertTrue(should_rescan(False, (0, 4, 0), (0, 4, 0), 8, 4))

    def test_rescan_when_marked_dirty(self):
        # loadRenderers()/updateChunkPositions() invalidate the cached list
        self.assertTrue(should_rescan(True, (0, 4, 0), (0, 4, 0), 8, 8))


class SodiumBudgetTest(unittest.TestCase):
    def test_upload_budget_scales_with_quality(self):
        budgets = [max_uploads(level) for level in (1, 2, 3)]
        self.assertEqual(budgets, sorted(budgets), 'higher levels upload at least as fast')
        self.assertLess(budgets[0], budgets[-1])

    def test_resort_interval_is_bounded(self):
        for level in range(LEVEL_COUNT):
            self.assertGreaterEqual(resort_interval(level), 0.0)
            self.assertLessEqual(resort_interval(level), 1000.0)

    def test_fast_level_is_the_only_one_that_throttles_sorting(self):
        self.assertGreater(resort_interval(1), 0.0)
        self.assertEqual(resort_interval(2), 0.0)
        self.assertEqual(resort_interval(3), 0.0)


if __name__ == '__main__':
    unittest.main()
