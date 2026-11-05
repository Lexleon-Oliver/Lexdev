import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.XMLConstants;
import javax.xml.validation.SchemaFactory;

/** Compilacao offline dos XSDs; nao acessa enderecos HTTP(S). */
class SchemaPackCheck {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Informe o caminho do nfe_v4.00.xsd");
        Path root = Path.of(args[0]);
        if (!Files.isRegularFile(root)) throw new IOException("XSD principal ausente: " + root);
        var factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file");
        factory.newSchema(root.toFile());
        System.out.println("XSD_COMPILATION=OK");
    }
}
