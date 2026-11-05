import hashlib
import shutil
from pathlib import Path
import tempfile
import unittest
import zipfile

from install_official_pl010g import PackageError, validate_zip, install

XSD_NS = 'http://www.w3.org/2001/XMLSchema'

def schema(content=''):
    return f'''<?xml version="1.0"?><xs:schema xmlns:xs="{XSD_NS}">{content}</xs:schema>'''.encode()


def package(entries, path):
    with zipfile.ZipFile(path, 'w', compression=zipfile.ZIP_DEFLATED) as out:
        for name, data in entries.items():
            out.writestr(name, data)
    return hashlib.sha256(path.read_bytes()).hexdigest()


FIELDS = ''.join(f'<xs:element name="{name}" type="xs:string"/>'
                 for name in ('vUnComLiq', 'vProdLiq', 'vProdLiqTot', 'gPgtoVinc'))


class OfficialPl010gImporterTest(unittest.TestCase):
    def setUp(self):
        self.ctx = tempfile.TemporaryDirectory()
        self.addCleanup(self.ctx.cleanup)
        self.root = Path(self.ctx.name)
        self.zip = self.root / 'schema.zip'
        self.entries = {
            'pl010g/nfe_v4.00.xsd': schema('<xs:include schemaLocation="leiauteNFe_v4.00.xsd"/>'),
            'pl010g/leiauteNFe_v4.00.xsd': schema(FIELDS),
        }

    def pack(self, edits=None):
        return package(self.entries if edits is None else edits, self.zip)

    def test_arquivo_valido_passa_inspecao_estatica(self):
        digest = self.pack()
        result = validate_zip(self.zip, digest)
        self.assertEqual(set(result), {'nfe_v4.00.xsd', 'leiauteNFe_v4.00.xsd'})

    def test_sha_incorreto_bloqueia(self):
        self.pack()
        with self.assertRaisesRegex(PackageError, 'SHA-256 diverge'):
            validate_zip(self.zip, '0' * 64)

    def test_include_ausente_bloqueia(self):
        files = self.entries.copy()
        files['pl010g/nfe_v4.00.xsd'] = schema('<xs:include schemaLocation="missing.xsd"/>')
        digest = self.pack(files)
        with self.assertRaisesRegex(PackageError, 'Dependência ausente'):
            validate_zip(self.zip, digest)

    def test_include_remoto_bloqueia(self):
        files = self.entries.copy()
        files['pl010g/nfe_v4.00.xsd'] = schema('<xs:include schemaLocation="https://example.com/a.xsd"/>')
        digest = self.pack(files)
        with self.assertRaisesRegex(PackageError, 'Dependência não local'):
            validate_zip(self.zip, digest)

    def test_path_traversal_bloqueia(self):
        files = self.entries.copy()
        files['../danger.xsd'] = schema()
        digest = self.pack(files)
        with self.assertRaisesRegex(PackageError, 'inseguro'):
            validate_zip(self.zip, digest)

    def test_versao_sem_campos_bloqueia(self):
        files = self.entries.copy()
        files['pl010g/leiauteNFe_v4.00.xsd'] = schema()
        digest = self.pack(files)
        with self.assertRaisesRegex(PackageError, 'campos esperados'):
            validate_zip(self.zip, digest)

    def test_arquivo_duplicado_bloqueia(self):
        files = self.entries.copy()
        files['outro/NFE_v4.00.xsd'] = schema()
        digest = self.pack(files)
        with self.assertRaisesRegex(PackageError, 'duplicado'):
            validate_zip(self.zip, digest)

    def test_importacao_completa_compila_e_registra_hashes(self):
        if shutil.which('java') is None:
            self.skipTest('Java não instalado no ambiente de teste')
        digest = self.pack()
        checker = Path(__file__).with_name('SchemaPackCheck.java')
        dest = self.root / 'PL_010g'
        manifest = install(self.zip, digest, dest, checker)
        self.assertEqual(manifest['zip_sha256'], digest)
        self.assertTrue((dest / 'nfe_v4.00.xsd').is_file())
        self.assertTrue((dest / 'leiauteNFe_v4.00.xsd').is_file())
        self.assertTrue((dest / 'SCHEMA_MANIFEST.json').is_file())

    def test_nao_sobrescreve_pacote_existente(self):
        digest = self.pack()
        dest = self.root / 'target'
        dest.mkdir()
        with self.assertRaisesRegex(PackageError, 'já existe'):
            install(self.zip, digest, dest, Path('nao-importa.java'))


if __name__ == '__main__':
    unittest.main()
