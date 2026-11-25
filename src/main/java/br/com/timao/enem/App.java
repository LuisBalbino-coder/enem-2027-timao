package br.com.timao.enem;

import br.com.timao.enem.api.Api;
import br.com.timao.enem.db.Db;
import br.com.timao.enem.db.Seed;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class App {

    private static final Logger LOG = LoggerFactory.getLogger(App.class);

    private App() {
    }

    public static void main(String[] args) {
        int porta = porta();
        Db.iniciar();
        Seed.carregar();

        Javalin app = Javalin.create(config -> {
            config.showJavalinBanner = false;
            config.staticFiles.add(arquivo -> {
                arquivo.directory = "/public";
                arquivo.location = Location.CLASSPATH;
            });
        });
        Api.registrar(app);
        app.start("127.0.0.1", porta);

        LOG.info("ENEM 2027 Timão no ar em http://127.0.0.1:{}", porta);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Encerrando servidor...");
            app.stop();
        }, "encerrar-enem"));
    }

    private static int porta() {
        String valor = System.getenv("PORT");
        if (valor == null || valor.isBlank()) {
            return 7077;
        }
        try {
            int numero = Integer.parseInt(valor.trim());
            return numero > 0 && numero < 65536 ? numero : 7077;
        } catch (NumberFormatException e) {
            return 7077;
        }
    }
}
