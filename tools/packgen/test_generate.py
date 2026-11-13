import hashlib, json, pathlib, re, subprocess, sys, unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / "backend/ccsampledata/resources/ccsampledata/import"
PACKS = ["tyres", "paints", "electrical", "cement", "plywood"]


def tree_hash():
    h = hashlib.sha256()
    for f in sorted(OUT.rglob("*")):
        if f.is_file():
            h.update(f.name.encode())
            h.update(f.read_bytes())
    return h.hexdigest()


def run():
    subprocess.run([sys.executable, "-I", str(ROOT / "tools/packgen/generate.py")], check=True)


class PackgenTest(unittest.TestCase):
    def test_regeneration_is_byte_identical(self):
        run()
        first = tree_hash()
        run()
        self.assertEqual(first, tree_hash())

    def test_all_five_packs_and_all_packs_dealer_exist(self):
        for p in PACKS:
            self.assertTrue((OUT / f"projectdata/pack-{p}.impex").exists(), p)
            self.assertTrue((OUT / f"orders/{p}.json").exists(), p)
        self.assertTrue((OUT / "orders/all-packs.json").exists())

    def test_essential_data_has_43_toggles(self):
        text = (OUT / "essential/essentialdata-cc-config.impex").read_text()
        self.assertEqual(43, len(re.findall(r"^;default;TOGGLE;;capability\.[A-Z]{2}-\d{2}\.enabled;true", text, re.M)))

    def test_order_plans_use_relative_dates_only(self):
        for f in (OUT / "orders").glob("*.json"):
            self.assertIsNone(re.search(r"\d{4}-\d{2}-\d{2}", f.read_text()), f.name)

    def test_only_synthetic_identities(self):
        for f in (OUT / "projectdata").glob("*.impex"):
            for email in re.findall(r"[\w.-]+@[\w.-]+", f.read_text()):
                self.assertTrue(email.endswith("example.invalid"), email)

    def test_scenario_fixtures(self):
        el = (OUT / "projectdata/pack-electrical.impex").read_text()
        self.assertIn(";EL-1033:ccProductCatalog:Online;EL-1034:ccProductCatalog:Online;FOLLOWUP;true", el)
        self.assertIn("EL-1033;Old PVC cable 2.5mm;Old PVC cable 2.5mm;;;unapproved", el)
        tyres = json.loads((OUT / "orders/tyres.json").read_text())
        rhythm = sorted(o["daysAgo"] for o in tyres if o["dealer"] == "dealer-tyres" and o["lines"][0]["product"] == "TY-1001")
        self.assertEqual([30, 60, 90, 120], rhythm)
        self.assertTrue(any(o["status"] == "PENDING" for o in tyres))
        self.assertTrue(any(o["dealer"] == "dealer-tyres-2" for o in tyres), "second dealer for isolation")
        ply = [o for o in json.loads((OUT / "orders/plywood.json").read_text()) if o["lines"][0]["product"] == "PW-1801"]
        qty = [o["lines"][0]["quantity"] for o in sorted(ply, key=lambda o: -o["daysAgo"])]
        self.assertEqual([100, 100, 95, 60, 60], qty)
        cement = json.loads((OUT / "orders/cement.json").read_text())
        self.assertGreaterEqual(len(cement), 5)

    def test_discontinued_item_per_pack_where_required(self):
        self.assertIn("unapproved", (OUT / "projectdata/pack-electrical.impex").read_text())


if __name__ == "__main__":
    unittest.main()
