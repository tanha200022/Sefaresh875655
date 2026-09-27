"""Validate actual SQL extracted from Store.java without Android SDK."""
from pathlib import Path
import re
import sqlite3
import unittest

class SchemaTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(':memory:')
        self.db.execute('PRAGMA foreign_keys=ON')
        source = (Path(__file__).parents[1] / 'app/src/main/java/ir/orderbook/app/Store.java').read_text()
        for sql in re.findall(r'db.execSQL\("([^"\n]+)"\)', source):
            self.db.execute(sql)
        self.db.execute("INSERT INTO companies VALUES(1,'الف','رضا','09123456789')")
        self.db.execute("INSERT INTO companies VALUES(2,'ب','علی','09123456780')")
    def add(self, company=1, buy=100, cartons=2, date='1405/07/04'):
        self.db.execute('INSERT INTO orders(company,name,buy,retail,days,cartons,delivery,note,created) VALUES(?,?,?,?,?,?,?,?,?)', (company,'کالا',buy,125,30,cartons,date,'',date))
    def test_company_isolation_and_order(self):
        self.add(date='1405/07/03'); self.add(company=2); self.add()
        rows = self.db.execute('SELECT company,created FROM orders WHERE company=1 ORDER BY created DESC,id DESC').fetchall()
        self.assertEqual(rows, [(1,'1405/07/04'),(1,'1405/07/03')])
    def test_invalid_values(self):
        for options in ({'buy':0}, {'cartons':0}, {'company':999}):
            with self.assertRaises(sqlite3.IntegrityError): self.add(**options)
    def test_duplicate_company(self):
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute("INSERT INTO companies(name,visitor,phone) VALUES('الف','رضا','09123456789')")
    def test_edit_delete(self):
        self.add(); self.add(company=2)
        self.db.execute('UPDATE orders SET cartons=5 WHERE id=1 AND company=1')
        self.assertEqual(self.db.execute('SELECT cartons FROM orders WHERE id=1').fetchone()[0],5)
        self.db.execute('DELETE FROM orders WHERE id=1')
        self.assertEqual(self.db.execute('SELECT company FROM orders').fetchall(),[(2,)])

if __name__ == '__main__': unittest.main()
