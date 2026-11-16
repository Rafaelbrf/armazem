package armazem;

import armazem.ddsql.Conexao;
import armazem.ddsql.HistoricoDDSQL;
import armazem.ddsql.ProdutoDDSQL;
import armazem.ddsql.UsuarioDDSQL;
import armazem.model.Nivel;
import armazem.model.Usuario;
import armazem.web.Servidor;

/*
 Ponto de partida do programa: é aqui que tudo começa quando você roda.
 */
public class Main {

    public static void main(String[] args) throws Exception {
        // Cria a pasta "dados" e as tabelas, se ainda não existirem
        Conexao.criarTabelas();

        // Na primeira vez (nenhum usuário cadastrado), cria o ADM inicial com todas as permissões
        UsuarioDDSQL usuarios = new UsuarioDDSQL();
        if (usuarios.contar() == 0) {
            Usuario admin = new Usuario();
            admin.setLogin("admin");
            admin.setNivel(Nivel.ADM);
            admin.setVer(true);
            admin.setCriar(true);
            admin.setEditar(true);
            admin.setExcluir(true);
            admin.setDefinirValor(true);
            admin.setAdicionar(true);
            admin.setRetirar(true);
            admin.setCriarUsuario(true);
            admin.setEditarUsuario(true);
            admin.setVerHistorico(true);
            String codigo = usuarios.criar(admin, "admin123");
            System.out.println("ADM criado -> login: admin | senha: admin123 | código: " + codigo);
        }

        // Bancos de versões antigas: quem ainda não tem código (ID) ganha um agora
        usuarios.gerarCodigosFaltando();

        // Se ainda não há nenhum produto, cadastra alguns exemplos (nome, tipo, quantidade, valor em centavos)
        ProdutoDDSQL produtos = new ProdutoDDSQL();
        HistoricoDDSQL historico = new HistoricoDDSQL();
        if (produtos.contar() == 0) {
            String[][] exemplos = {
                    {"Parafuso 6mm", "Ferragem", "500", "35"},
                    {"Martelo", "Ferramenta", "30", "2990"},
                    {"Caixa de papelão", "Embalagem", "200", "450"},
                    {"Cabo de rede 2m", "Eletrônico", "80", "1800"}
            };
            for (String[] ex : exemplos) {
                int id = produtos.criar(ex[0], ex[1], Integer.parseInt(ex[2]), Long.parseLong(ex[3]));
                historico.registrar("sistema", "", "Criou produto de exemplo", id, ex[0]);
            }
            System.out.println("Produtos de exemplo cadastrados.");
        }

        // Liga o servidor do site (porta 8080, ou a que estiver na variável PORT)
        int porta = 8080;
        if (System.getenv("PORT") != null) {
            porta = Integer.parseInt(System.getenv("PORT"));
        }
        new Servidor(porta).iniciar();
    }
}
