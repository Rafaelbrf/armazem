package armazem.web;

import armazem.model.Dinheiro;
import armazem.model.Nivel;
import armazem.model.Produto;
import armazem.model.Registro;
import armazem.model.Usuario;

import java.util.List;

/*
 Aqui ficam os métodos que MONTAM o HTML de cada página.
 Cada método devolve um texto (String) com o HTML pronto.
 O CSS (visual) fica no arquivo estilo.css.
 */
public class Paginas {

    // Troca caracteres especiais por códigos HTML.
    // Isso impede que alguém digite <script> num campo e o site execute esse código.
    public static String esc(String texto) {
        if (texto == null) {
            return "";
        }
        texto = texto.replace("&", "&amp;");
        texto = texto.replace("<", "&lt;");
        texto = texto.replace(">", "&gt;");
        texto = texto.replace("\"", "&quot;");
        return texto;
    }

    // Moldura comum de todas as páginas: topo com o menu + o conteúdo no meio.
    // Se u == null (ninguém logado), o menu fica vazio.
    public static String layout(String titulo, Usuario u, String conteudo) {
        String menu = "";
        if (u != null) {
            menu += "<a href=\"/produtos\">Produtos</a>";
            // cada link só aparece para quem tem a permissão correspondente
            if (u.podeCriar()) {
                menu += "<a href=\"/produtos/novo\">Novo produto</a>";
            }
            if (u.podeVerHistorico()) {
                menu += "<a href=\"/historico\">Histórico</a>";
            }
            if (u.podeCriarUsuario() || u.podeEditarUsuario()) {
                menu += "<a href=\"/usuarios\">Usuários</a>";
            }
            menu += "<span class=\"espaco\"></span>";
            menu += "<span class=\"quem\">" + esc(u.getLogin()) + " (" + esc(u.getCodigo()) + ")</span>";
            menu += "<a href=\"/sair\">Sair</a>";
        }

        return "<!DOCTYPE html><html lang=\"pt-BR\"><head><meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>" + esc(titulo) + " - Armazém</title>"
                + "<link rel=\"stylesheet\" href=\"/estilo.css\"></head><body>"
                + "<header><span class=\"logo\">Armazém</span><nav>" + menu + "</nav></header>"
                + "<main>" + conteudo + "</main></body></html>";
    }

    // Caixinha colorida de aviso (verde para sucesso, vermelho para erro)
    public static String aviso(String texto, boolean erro) {
        if (texto == null || texto.equals("")) {
            return "";
        }
        String classe = erro ? "erro" : "ok";
        return "<div class=\"aviso " + classe + "\">" + esc(texto) + "</div>";
    }

    // Página simples só com uma mensagem (usada para "sem permissão", por exemplo)
    public static String mensagem(String titulo, Usuario u, String texto) {
        return layout(titulo, u, aviso(texto, true));
    }

    // LOGIN
    public static String login(String erro) {
        String html = "<div class=\"caixa login\"><h1>Entrar</h1>"
                + aviso(erro, true)
                + "<form method=\"post\" action=\"/login\">"
                + "<label>Login <input name=\"login\" required autofocus></label>"
                + "<label>Senha <input name=\"senha\" type=\"password\" required></label>"
                + "<button class=\"primario\">Entrar</button>"
                + "</form></div>";
        return layout("Entrar", null, html);
    }

    // LISTA DE PRODUTOS
    // msg e erro: aviso para mostrar no topo (por exemplo, depois de adicionar ou retirar estoque)
    public static String produtos(Usuario u, List<Produto> lista, List<String> tipos,
                                  String busca, String tipoEscolhido, String msg, boolean erro) {
        String html = "<h1>Produtos</h1>" + aviso(msg, erro);

        // formulário de busca e filtro
        html += "<form class=\"filtro\" method=\"get\" action=\"/produtos\">";
        html += "<input name=\"q\" placeholder=\"Buscar por nome\" value=\"" + esc(busca) + "\">";
        html += "<select name=\"tipo\"><option value=\"\">Todos os tipos</option>";
        for (String t : tipos) {
            String marcado = t.equals(tipoEscolhido) ? " selected" : "";
            html += "<option" + marcado + ">" + esc(t) + "</option>";
        }
        html += "</select><button>Filtrar</button>";
        if (u.podeCriar()) {
            html += "<a class=\"botao primario\" href=\"/produtos/novo\">+ Novo produto</a>";
        }
        html += "</form>";

        if (lista.isEmpty()) {
            html += "<div class=\"vazio\">Nenhum produto encontrado.</div>";
            return layout("Produtos", u, html);
        }

        // tabela de produtos
        boolean mostrarAcoes = u.podeEditar() || u.podeExcluir();
        boolean mostrarEstoque = u.podeAdicionar() || u.podeRetirar();
        html += "<table><thead><tr><th>ID</th><th>Nome</th><th>Tipo</th><th>Quantidade</th><th>Valor</th>";
        if (mostrarEstoque) {
            html += "<th>Estoque</th>";
        }
        if (mostrarAcoes) {
            html += "<th>Ações</th>";
        }
        html += "</tr></thead><tbody>";

        for (Produto p : lista) {   // uma linha da tabela para cada produto
            html += "<tr><td>" + p.getId() + "</td>"
                    + "<td>" + esc(p.getNome()) + "</td>"
                    + "<td>" + esc(p.getTipo()) + "</td>"
                    + "<td>" + p.getQuantidade() + "</td>"
                    + "<td>" + Dinheiro.formatar(p.getValorCentavos()) + "</td>";

            // formulário para adicionar ou retirar quantidade deste produto.
            // Os dois botões enviam o mesmo campo (a quantidade), cada um para um endereço
            // diferente (formaction). Cada botão só aparece para quem tem a permissão.
            if (mostrarEstoque) {
                html += "<td><form class=\"mover\" method=\"post\" action=\"/produtos/adicionar\">"
                        + "<input type=\"hidden\" name=\"id\" value=\"" + p.getId() + "\">"
                        + "<input type=\"number\" name=\"quantidade\" min=\"1\" value=\"1\" required>";
                if (u.podeAdicionar()) {
                    html += "<button class=\"mais\" formaction=\"/produtos/adicionar\">+ Adicionar</button>";
                }
                if (u.podeRetirar()) {
                    html += "<button class=\"menos\" formaction=\"/produtos/retirar\">- Retirar</button>";
                }
                html += "</form></td>";
            }

            if (mostrarAcoes) {
                html += "<td class=\"acoes\">";
                if (u.podeEditar()) {
                    html += "<a class=\"botao\" href=\"/produtos/editar?id=" + p.getId() + "\">Editar</a>";
                }
                if (u.podeExcluir()) {
                    html += "<form method=\"post\" action=\"/produtos/excluir\" "
                            + "onsubmit=\"return confirm('Excluir este produto?')\">"
                            + "<input type=\"hidden\" name=\"id\" value=\"" + p.getId() + "\">"
                            + "<button class=\"perigo\">Excluir</button></form>";
                }
                html += "</td>";
            }
            html += "</tr>";
        }
        html += "</tbody></table>";
        return layout("Produtos", u, html);
    }

    // FORMULÁRIO DE PRODUTO
    // Serve para criar (produto == null) e para editar (produto preenchido).
    // O campo do valor só aparece para quem tem a permissão de definir valor.
    public static String formProduto(Usuario u, Produto produto, List<String> tipos, String erro) {
        boolean edicao = (produto != null);
        String nome = edicao ? produto.getNome() : "";
        String tipo = edicao ? produto.getTipo() : "";
        int quantidade = edicao ? produto.getQuantidade() : 0;
        long valor = edicao ? produto.getValorCentavos() : 0;

        String html = "<div class=\"caixa\">";
        html += "<h1>" + (edicao ? "Editar produto #" + produto.getId() : "Novo produto") + "</h1>";
        html += aviso(erro, true);
        html += "<form method=\"post\" action=\"" + (edicao ? "/produtos/editar" : "/produtos/novo") + "\">";
        if (edicao) {
            // campo escondido que leva o id do produto junto com o formulário
            html += "<input type=\"hidden\" name=\"id\" value=\"" + produto.getId() + "\">";
        }
        html += "<label>Nome <input name=\"nome\" required maxlength=\"100\" value=\"" + esc(nome) + "\"></label>";
        html += "<label>Tipo <input name=\"tipo\" list=\"tipos\" required maxlength=\"50\" value=\"" + esc(tipo) + "\">";
        html += "<datalist id=\"tipos\">";      // sugestões com os tipos que já existem
        for (String t : tipos) {
            html += "<option value=\"" + esc(t) + "\">";
        }
        html += "</datalist></label>";
        html += "<label>Quantidade <input name=\"quantidade\" type=\"number\" min=\"0\" required value=\""
                + quantidade + "\"></label>";

        if (u.podeDefinirValor()) {
            html += "<label>Valor em reais (por exemplo 12,50) <input name=\"valor\" required "
                    + "inputmode=\"decimal\" value=\"" + Dinheiro.paraCampo(valor) + "\"></label>";
        } else {
            html += "<p class=\"dica\">Você não tem permissão para definir o valor do produto.</p>";
        }

        html += "<div class=\"linha\"><button class=\"primario\">Salvar</button>"
                + "<a class=\"botao\" href=\"/produtos\">Cancelar</a></div>";
        html += "</form></div>";
        return layout(edicao ? "Editar produto" : "Novo produto", u, html);
    }

    // HISTÓRICO
    // Mostra quem fez o quê. produtoId maior que zero significa que está filtrando por um produto.
    public static String historico(Usuario u, List<Registro> lista, int produtoId) {
        String html = "<h1>Histórico</h1>";

        // filtro: ver só o que aconteceu com um produto (pelo ID dele)
        html += "<form class=\"filtro\" method=\"get\" action=\"/historico\">"
                + "<input name=\"produto\" type=\"number\" min=\"1\" placeholder=\"Filtrar pelo ID do produto\" "
                + "value=\"" + (produtoId > 0 ? String.valueOf(produtoId) : "") + "\">"
                + "<button>Filtrar</button>"
                + "<a class=\"botao\" href=\"/historico\">Mostrar tudo</a></form>";

        if (lista.isEmpty()) {
            html += "<div class=\"vazio\">Nenhum registro encontrado.</div>";
            return layout("Histórico", u, html);
        }

        html += "<table><thead><tr><th>Data e hora</th><th>Quem</th><th>O que fez</th>"
                + "<th>ID do produto</th><th>Detalhes</th></tr></thead><tbody>";
        for (Registro r : lista) {
            // se produtoId for 0, não teve produto envolvido: mostra um traço
            String idProduto = r.getProdutoId() > 0 ? String.valueOf(r.getProdutoId()) : "-";
            // mostra o nome e, quando existe, o código (ID) de quem fez
            String quem = esc(r.getUsuario());
            if (r.getCodigo() != null && !r.getCodigo().equals("")) {
                quem += " <span class=\"codigo\">" + esc(r.getCodigo()) + "</span>";
            }
            html += "<tr><td>" + esc(r.getDataHora()) + "</td>"
                    + "<td>" + quem + "</td>"
                    + "<td>" + esc(r.getAcao()) + "</td>"
                    + "<td>" + idProduto + "</td>"
                    + "<td>" + esc(r.getDetalhes()) + "</td></tr>";
        }
        html += "</tbody></table>";
        html += "<p class=\"dica\">Mostrando os 300 registros mais recentes.</p>";
        return layout("Histórico", u, html);
    }

    // USUÁRIOS
    private static String caixinha(String nome, String texto, boolean marcada) {
        return "<label class=\"check\"><input type=\"checkbox\" name=\"" + nome + "\" value=\"1\""
                + (marcada ? " checked" : "") + "> " + texto + "</label>";
    }

    // Caixinhas das permissões. 'quemEdita' é quem está logado e 'alvo' é o usuário que está sendo
    // editado (null quando é um usuário novo). Cada pessoa só vê as permissões que ELA mesma tem,
    // assim ninguém consegue dar para outro mais poder do que tem. O ADM vê todas.
    private static String caixinhas(Usuario quemEdita, Usuario alvo) {
        String html = "";
        if (quemEdita.podeVer())           { html += caixinha("ver", "Ver produtos", alvo != null && alvo.podeVer()); }
        if (quemEdita.podeCriar())         { html += caixinha("criar", "Criar produtos", alvo != null && alvo.podeCriar()); }
        if (quemEdita.podeEditar())        { html += caixinha("editar", "Editar produtos", alvo != null && alvo.podeEditar()); }
        if (quemEdita.podeExcluir())       { html += caixinha("excluir", "Excluir produtos", alvo != null && alvo.podeExcluir()); }
        if (quemEdita.podeDefinirValor())  { html += caixinha("definirvalor", "Definir valor dos produtos", alvo != null && alvo.podeDefinirValor()); }
        if (quemEdita.podeAdicionar())     { html += caixinha("adicionar", "Adicionar ao estoque", alvo != null && alvo.podeAdicionar()); }
        if (quemEdita.podeRetirar())       { html += caixinha("retirar", "Retirar do estoque", alvo != null && alvo.podeRetirar()); }
        if (quemEdita.podeCriarUsuario())  { html += caixinha("criarusuario", "Criar usuários", alvo != null && alvo.podeCriarUsuario()); }
        if (quemEdita.podeEditarUsuario()) { html += caixinha("editarusuario", "Editar usuários", alvo != null && alvo.podeEditarUsuario()); }
        if (quemEdita.podeVerHistorico())  { html += caixinha("historico", "Ver histórico", alvo != null && alvo.podeVerHistorico()); }
        return html;
    }

    // Caixa de escolha do cargo, com os cargos que quem está logado pode dar.
    // O ADM pode dar qualquer cargo; os outros só cargos menores que o seu.
    private static String selectCargo(Usuario logado, int selecionado) {
        String html = "<select name=\"nivel\">";
        for (int n = Nivel.OPERADOR; n <= logado.nivelMaximoQuePodeDar(); n++) {
            html += "<option value=\"" + n + "\"" + (n == selecionado ? " selected" : "") + ">"
                    + Nivel.nome(n) + "</option>";
        }
        return html + "</select>";
    }

    // Formulário para criar um usuário novo
    private static String formNovoUsuario(Usuario logado) {
        String html = "<div class=\"caixa larga\"><h2>Criar novo usuário</h2>";

        // quem tem o cargo mais baixo não tem cargo menor para dar
        if (logado.nivelMaximoQuePodeDar() < Nivel.OPERADOR) {
            return html + "<p class=\"dica\">O seu cargo é o mais baixo, então você não pode criar usuários.</p></div>";
        }
        if (!logado.isAdmin()) {
            html += "<p class=\"dica\">Você só pode criar usuários de cargo menor que o seu, "
                    + "com permissões que você mesmo tem.</p>";
        }
        html += "<form method=\"post\" action=\"/usuarios/novo\" class=\"grade\">"
                + "<label>Nome (login) <input name=\"login\" required maxlength=\"40\"></label>"
                + "<label>Cargo " + selectCargo(logado, Nivel.OPERADOR) + "</label>"
                + "<label>Senha (mínimo 6 caracteres) <input name=\"senha\" type=\"password\" required minlength=\"6\"></label>"
                + "<fieldset><legend>O que este usuário pode fazer</legend>" + caixinhas(logado, null) + "</fieldset>"
                + "<button class=\"primario\">Criar usuário</button></form></div>";
        return html;
    }

    // Página com o formulário de criar e a lista de usuários que a pessoa pode editar
    public static String usuarios(Usuario logado, List<Usuario> lista, String msg, boolean erro) {
        String html = "<h1>Usuários</h1>" + aviso(msg, erro);

        if (logado.podeCriarUsuario()) {
            html += formNovoUsuario(logado);
        }

        // um cartão para cada usuário que a pessoa pode editar
        if (logado.podeEditarUsuario()) {
            html += "<h2>Usuários que você pode editar</h2>";
            if (lista.isEmpty()) {
                html += "<div class=\"vazio\">Nenhum usuário de cargo menor que o seu.</div>";
            }
            for (Usuario u : lista) {
                boolean souEu = (u.getId() == logado.getId());

                html += "<div class=\"caixa larga\"><h2>" + esc(u.getLogin())
                        + " <span class=\"codigo\">" + esc(u.getCodigo()) + "</span> "
                        + "<span class=\"selo\">" + Nivel.nome(u.getNivel()) + "</span>"
                        + (souEu ? " <span class=\"selo cinza\">você</span>" : "") + "</h2>"
                        + "<p class=\"dica\">Permissões: " + esc(u.resumoPermissoes()) + "</p>"
                        + "<div class=\"linha quebra\">"
                        + "<a class=\"botao primario\" href=\"/usuarios/editar?id=" + u.getId() + "\">Editar usuário</a>";

                // só o ADM exclui usuários, e ninguém exclui a si mesmo
                if (logado.isAdmin() && !souEu) {
                    html += "<form method=\"post\" action=\"/usuarios/excluir\" "
                            + "onsubmit=\"return confirm('Excluir este usuário?')\">"
                            + "<input type=\"hidden\" name=\"id\" value=\"" + u.getId() + "\">"
                            + "<button class=\"perigo\">Excluir usuário</button></form>";
                }
                html += "</div></div>";
            }
        }
        return layout("Usuários", logado, html);
    }

    // TELA DE EDITAR USUÁRIO: nome, cargo, senha e todas as permissões em um só lugar
    public static String editarUsuario(Usuario logado, Usuario alvo, String msg, boolean erro) {
        boolean souEu = (alvo.getId() == logado.getId());

        String html = "<div class=\"caixa larga\"><h1>Editar usuário</h1>" + aviso(msg, erro);
        html += "<p class=\"dica\">Código (ID) deste usuário: <span class=\"codigo\">" + esc(alvo.getCodigo())
                + "</span>. O número fica sempre o mesmo. Só a sigla muda se ele mudar de cargo.</p>";

        html += "<form method=\"post\" action=\"/usuarios/editar\" class=\"grade\">"
                + "<input type=\"hidden\" name=\"id\" value=\"" + alvo.getId() + "\">"
                + "<label>Nome (login) <input name=\"login\" required maxlength=\"40\" value=\"" + esc(alvo.getLogin()) + "\"></label>";

        // o ADM não pode mudar o próprio cargo (senão poderia ficar sem nenhum ADM)
        if (souEu) {
            html += "<label>Cargo <input value=\"" + Nivel.nome(alvo.getNivel()) + "\" disabled></label>"
                    + "<input type=\"hidden\" name=\"nivel\" value=\"" + alvo.getNivel() + "\">";
        } else {
            html += "<label>Cargo " + selectCargo(logado, alvo.getNivel()) + "</label>";
        }

        html += "<label>Nova senha (deixe vazio para não mudar) "
                + "<input name=\"senha\" type=\"password\" minlength=\"6\" autocomplete=\"new-password\"></label>"
                + "<span></span>";

        html += "<fieldset><legend>O que este usuário pode fazer</legend>";
        if (!logado.isAdmin()) {
            html += "<p class=\"dica\">Você só vê as permissões que você mesmo tem. As outras ficam como estão.</p>";
        }
        html += caixinhas(logado, alvo) + "</fieldset>";

        html += "<div class=\"linha\"><button class=\"primario\">Salvar</button>"
                + "<a class=\"botao\" href=\"/usuarios\">Cancelar</a></div>"
                + "</form></div>";
        return layout("Editar usuário", logado, html);
    }
}
