package br.com.timao.enem.db;

import org.h2.tools.RunScript;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Banco H2 em arquivo, sem servidor externo. */
public final class Db {

    private static String url;

    private Db() {
    }

    public static synchronized void iniciar() {
        try {
            String base = System.getenv().getOrDefault("ENEM_DB_PATH", "data");
            Path dir = Paths.get(base).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            url = "jdbc:h2:file:" + dir.resolve("enem2027").toString().replace('\\', '/')
                    + ";DB_CLOSE_DELAY=-1;AUTO_RECONNECT=TRUE";
            Class.forName("org.h2.Driver");
            try (Connection conn = conectar();
                 Reader schema = new InputStreamReader(
                         Db.class.getResourceAsStream("/schema.sql"), StandardCharsets.UTF_8)) {
                RunScript.execute(conn, schema);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível iniciar o banco H2", e);
        }
    }

    public static Connection conectar() throws SQLException {
        if (url == null) {
            throw new IllegalStateException("Db.iniciar() precisa ser chamado antes.");
        }
        return DriverManager.getConnection(url, "sa", "");
    }

    public interface Trabalho<T> {
        T executar(Connection conn) throws SQLException;
    }

    /** Abre e fecha uma conexão, traduzindo SQLException em erro de runtime. */
    public static <T> T comConexao(Trabalho<T> trabalho) {
        try (Connection conn = conectar()) {
            return trabalho.executar(conn);
        } catch (SQLException e) {
            throw new IllegalStateException("Erro de banco: " + e.getMessage(), e);
        }
    }
}
