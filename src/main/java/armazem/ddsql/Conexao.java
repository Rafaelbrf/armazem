package armazem.ddsql;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/*
 Cuida da conexão com os bancos de dados.

 Usamos TRÊS bancos SQLite (cada um é só um arquivo dentro da pasta "dados"):
   produtos.db guarda os produtos (cada um com seu ID)
   usuarios.db guarda os logins e as permissões
   historico.db guarda quem fez o quê (e o ID do produto envolvido)
 */
public class Conexao {

    public static Connection produtos() throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:dados/produtos.db");
    }

    public static Connection usuarios() throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:dados/usuarios.db");
    }

    public static Connection historico() throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:dados/historico.db");
    }

    // Cria a pasta e as tabelas (só cria se ainda não existirem)
    public static void criarTabelas() throws Exception {
        new File("dados").mkdirs();

        // banco de produtos
        Connection c1 = produtos();
        Statement s1 = c1.createStatement();
        s1.execute("CREATE TABLE IF NOT EXISTS produtos ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "   // o banco numera sozinho
                + "nome TEXT NOT NULL, "
                + "tipo TEXT NOT NULL, "
                + "quantidade INTEGER NOT NULL, "
                + "valor_centavos INTEGER NOT NULL DEFAULT 0)");   // valor em centavos (1250 é R$ 12,50)
        // banco antigo (sem a coluna do valor): adiciona a coluna
        adicionarColuna(c1, "produtos", "valor_centavos", "INTEGER NOT NULL DEFAULT 0");
        c1.close();

        // banco de usuários
        Connection c2 = usuarios();
        Statement s2 = c2.createStatement();
        s2.execute("CREATE TABLE IF NOT EXISTS usuarios ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "    // número de entrada, nunca se repete
                + "login TEXT NOT NULL UNIQUE COLLATE NOCASE, "   // não repete login (Ana e ana contam como iguais)
                + "senha_hash TEXT NOT NULL, "
                + "salt TEXT NOT NULL, "
                + "admin INTEGER NOT NULL, "                  // 1 = ADM, 0 = outro cargo
                + "pode_ver INTEGER NOT NULL, "
                + "pode_criar INTEGER NOT NULL, "
                + "pode_editar INTEGER NOT NULL, "
                + "pode_excluir INTEGER NOT NULL, "
                + "pode_criar_usuario INTEGER NOT NULL DEFAULT 0, "
                + "pode_ver_historico INTEGER NOT NULL DEFAULT 0, "
                + "pode_adicionar INTEGER NOT NULL DEFAULT 0, "
                + "pode_retirar INTEGER NOT NULL DEFAULT 0, "
                + "pode_editar_usuario INTEGER NOT NULL DEFAULT 0, "
                + "pode_definir_valor INTEGER NOT NULL DEFAULT 0, "
                + "nivel INTEGER NOT NULL DEFAULT 1, "        // o cargo: 1 Operador até 4 ADM
                + "codigo TEXT)");                            // o código (ID), por exemplo GER-0002

        // Se o banco é de uma versão ANTIGA (sem as colunas mais novas), adiciona elas.
        // Se a coluna já existe, dá erro e a gente simplesmente ignora.
        adicionarColuna(c2, "usuarios", "pode_criar_usuario", "INTEGER NOT NULL DEFAULT 0");
        adicionarColuna(c2, "usuarios", "pode_ver_historico", "INTEGER NOT NULL DEFAULT 0");
        adicionarColuna(c2, "usuarios", "pode_adicionar", "INTEGER NOT NULL DEFAULT 0");
        adicionarColuna(c2, "usuarios", "pode_retirar", "INTEGER NOT NULL DEFAULT 0");
        adicionarColuna(c2, "usuarios", "pode_editar_usuario", "INTEGER NOT NULL DEFAULT 0");
        adicionarColuna(c2, "usuarios", "pode_definir_valor", "INTEGER NOT NULL DEFAULT 0");
        adicionarColuna(c2, "usuarios", "codigo", "TEXT");

        // Se a coluna do cargo acabou de ser criada (banco antigo), quem era ADM vira cargo 4 (ADM).
        boolean cargoNovo = adicionarColuna(c2, "usuarios", "nivel", "INTEGER NOT NULL DEFAULT 1");
        if (cargoNovo) {
            c2.createStatement().execute("UPDATE usuarios SET nivel = 4 WHERE admin = 1");
        }

        // O índice único garante que dois usuários NUNCA tenham o mesmo código
        c2.createStatement().execute("CREATE UNIQUE INDEX IF NOT EXISTS indice_codigo ON usuarios (codigo)");
        c2.close();

        // banco do histórico
        Connection c3 = historico();
        Statement s3 = c3.createStatement();
        s3.execute("CREATE TABLE IF NOT EXISTS historico ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "data_hora TEXT NOT NULL, "
                + "usuario TEXT NOT NULL, "
                + "usuario_codigo TEXT NOT NULL DEFAULT '', "  // código (ID) de quem fez
                + "acao TEXT NOT NULL, "
                + "produto_id INTEGER NOT NULL DEFAULT 0, "   // ID do produto (0 = nenhum)
                + "detalhes TEXT NOT NULL DEFAULT '')");
        adicionarColuna(c3, "historico", "usuario_codigo", "TEXT NOT NULL DEFAULT ''");
        c3.close();
    }

    // Tenta adicionar uma coluna numa tabela. Devolve true se criou e false se ela já existia.
    private static boolean adicionarColuna(Connection c, String tabela, String coluna, String definicao) {
        try {
            c.createStatement().execute("ALTER TABLE " + tabela + " ADD COLUMN " + coluna + " " + definicao);
            return true;
        } catch (Exception e) {
            return false;   // a coluna já existia: não precisa fazer nada
        }
    }
}
