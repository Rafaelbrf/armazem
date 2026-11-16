package armazem.web;

import armazem.ddsql.HistoricoDDSQL;
import armazem.ddsql.ProdutoDDSQL;
import armazem.ddsql.UsuarioDDSQL;
import armazem.model.Dinheiro;
import armazem.model.Nivel;
import armazem.model.Produto;
import armazem.model.Usuario;
import armazem.seguranca.Senha;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 O servidor do site. Ele recebe cada pedido do navegador (um "endereço"),
 decide o que fazer e devolve uma página HTML.

Como funciona o login:
O usuário envia login e senha.
Se estiver certo, criamos um "token" aleatório e guardamos em 'sessoes'
(cada token aponta para o id do usuário).
O token vai para o navegador num cookie. Em cada pedido seguinte, lemos o cookie
e descobrimos quem está logado.

 Histórico: toda ação importante chama o método registrar(), que grava
 quem fez, o quê e (quando tem) o ID do produto no banco historico.db.
 */
public class Servidor {

    private int porta;
    private ProdutoDDSQL produtoDDSQL = new ProdutoDDSQL();
    private UsuarioDDSQL usuarioDDSQL = new UsuarioDDSQL();
    private HistoricoDDSQL historicoDDSQL = new HistoricoDDSQL();

    // guarda quem está logado: cada token do cookie aponta para o id do usuário
    private Map<String, Integer> sessoes = new HashMap<>();

    // guarda um aviso para mostrar na próxima página (lista de produtos ou de usuários) que o usuário abrir.
    // A chave é o id do usuário; o valor tem 2 textos: o tipo ("ok" ou "erro") e a mensagem.
    private Map<Integer, String[]> avisos = new HashMap<>();

    public Servidor(int porta) {
        this.porta = porta;
    }

    // Liga o servidor. Todo pedido cai no método "tratar".
    public void iniciar() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(porta), 0);
        server.createContext("/", exchange -> tratar(exchange));
        server.start();
        System.out.println("Armazém rodando em http://localhost:" + porta);
    }

    // Recebe o pedido; se der algum erro inesperado, mostra uma página de erro.
    private void tratar(HttpExchange ex) throws IOException {
        try {
            rotear(ex);
        } catch (Exception e) {
            e.printStackTrace();
            enviar(ex, 500, Paginas.mensagem("Erro", null, "Erro interno no servidor."));
        } finally {
            ex.close();
        }
    }

    // ROTAS
    // "Rota" = um endereço do site. Aqui decidimos o que cada endereço faz.
    private void rotear(HttpExchange ex) throws Exception {
        String metodo = ex.getRequestMethod();        // GET (abrir página) ou POST (enviar formulário)
        String caminho = ex.getRequestURI().getPath(); // ex: /produtos/novo
        Map<String, String> p = lerParametros(ex);     // dados enviados (formulário ou URL)

        // rotas públicas (não precisam estar logado)
        if (caminho.equals("/estilo.css")) {
            enviarCss(ex);
            return;
        }
        if (caminho.equals("/login")) {
            if (metodo.equals("POST")) {
                fazerLogin(ex, p);
            } else {
                enviar(ex, 200, Paginas.login(null));
            }
            return;
        }
        if (caminho.equals("/sair")) {
            Usuario quemSai = usuarioLogado(ex);
            if (quemSai != null) {
                registrar(quemSai, "Saiu do sistema", 0, "");
            }
            sessoes.remove(lerToken(ex));
            ex.getResponseHeaders().add("Set-Cookie", "SESSAO=; Max-Age=0; Path=/");
            redirecionar(ex, "/login");
            return;
        }

        // daqui para baixo, só quem está logado
        Usuario u = usuarioLogado(ex);
        if (u == null) {
            redirecionar(ex, "/login");
            return;
        }

        if (caminho.equals("/")) {
            redirecionar(ex, "/produtos");

        } else if (caminho.equals("/produtos")) {
            if (!u.podeVer()) { semPermissao(ex, u); return; }
            String busca = p.getOrDefault("q", "").trim();
            String tipo = p.getOrDefault("tipo", "");
            // pega (e apaga) o aviso guardado para este usuário, se existir
            String[] aviso = avisos.remove(u.getId());
            String msg = aviso == null ? null : aviso[1];
            boolean erro = aviso != null && aviso[0].equals("erro");
            enviar(ex, 200, Paginas.produtos(u, produtoDDSQL.listar(busca, tipo),
                    produtoDDSQL.listarTipos(), busca, tipo, msg, erro));

        } else if (caminho.equals("/produtos/novo")) {
            if (!u.podeCriar()) { semPermissao(ex, u); return; }
            if (metodo.equals("POST")) {
                salvarProduto(ex, u, p, false);
            } else {
                enviar(ex, 200, Paginas.formProduto(u, null, produtoDDSQL.listarTipos(), null));
            }

        } else if (caminho.equals("/produtos/editar")) {
            if (!u.podeEditar()) { semPermissao(ex, u); return; }
            if (metodo.equals("POST")) {
                salvarProduto(ex, u, p, true);
            } else {
                Produto produto = produtoDDSQL.buscar(paraInt(p.get("id")));
                if (produto == null) {
                    redirecionar(ex, "/produtos");
                } else {
                    enviar(ex, 200, Paginas.formProduto(u, produto, produtoDDSQL.listarTipos(), null));
                }
            }

        } else if (caminho.equals("/produtos/adicionar") && metodo.equals("POST")) {
            if (!u.podeAdicionar()) { semPermissao(ex, u); return; }
            moverEstoque(ex, u, p, true);

        } else if (caminho.equals("/produtos/retirar") && metodo.equals("POST")) {
            if (!u.podeRetirar()) { semPermissao(ex, u); return; }
            moverEstoque(ex, u, p, false);

        } else if (caminho.equals("/produtos/excluir") && metodo.equals("POST")) {
            if (!u.podeExcluir()) { semPermissao(ex, u); return; }
            excluirProduto(ex, u, paraInt(p.get("id")));

        } else if (caminho.equals("/historico")) {
            if (!u.podeVerHistorico()) { semPermissao(ex, u); return; }
            // se a URL trouxe produto=3, filtra pelo produto 3; se não trouxe, paraInt devolve -1 (que significa tudo)
            int produtoId = paraInt(p.get("produto"));
            enviar(ex, 200, Paginas.historico(u, historicoDDSQL.listar(produtoId), produtoId));

        } else if (caminho.startsWith("/usuarios")) {
            // cada rota de usuários confere a permissão e a hierarquia por dentro
            rotasUsuarios(ex, u, caminho, metodo, p);

        } else {
            enviar(ex, 404, Paginas.mensagem("Não encontrado", u, "Página não encontrada."));
        }
    }

    // LOGIN
    private void fazerLogin(HttpExchange ex, Map<String, String> p) throws Exception {
        String login = p.getOrDefault("login", "").trim();
        Usuario u = usuarioDDSQL.autenticar(login, p.getOrDefault("senha", ""));
        if (u == null) {
            // fica registrado quem tentou entrar e errou
            historicoDDSQL.registrar(login, "", "Tentativa de login falhou", 0, "login ou senha incorretos");
            enviar(ex, 401, Paginas.login("Login ou senha incorretos."));
            return;
        }
        String token = Senha.novoToken();
        sessoes.put(token, u.getId());
        registrar(u, "Entrou no sistema", 0, "");
        // HttpOnly: o JavaScript da página não consegue ler o cookie (mais seguro)
        ex.getResponseHeaders().add("Set-Cookie", "SESSAO=" + token + "; HttpOnly; SameSite=Strict; Path=/");
        redirecionar(ex, "/produtos");
    }

    // PRODUTOS
    // Salva um produto novo (edicao = false) ou altera um existente (edicao = true).
    private void salvarProduto(HttpExchange ex, Usuario u, Map<String, String> p, boolean edicao) throws Exception {
        String nome = p.getOrDefault("nome", "").trim();
        String tipo = p.getOrDefault("tipo", "").trim();
        int quantidade = paraInt(p.get("quantidade"));
        int id = paraInt(p.get("id"));

        // na edição, guarda como o produto era ANTES (para comparar e para manter o valor se preciso)
        Produto antigo = edicao ? produtoDDSQL.buscar(id) : null;
        if (edicao && antigo == null) {
            redirecionar(ex, "/produtos");
            return;
        }

        // O valor: só quem tem a permissão "Definir valor" pode colocar ou mudar.
        // Quem não tem a permissão não consegue mexer, mesmo que tente enviar o campo na mão.
        long valor = 0;
        boolean valorInvalido = false;
        if (u.podeDefinirValor()) {
            valor = Dinheiro.paraCentavos(p.getOrDefault("valor", ""));
            valorInvalido = (valor < 0);          // -1 significa que o texto não era um valor válido
        } else if (edicao) {
            valor = antigo.getValorCentavos();    // sem permissão: o valor continua como estava
        }

        // validação: não deixa salvar campo vazio, quantidade negativa ou valor inválido
        if (nome.equals("") || tipo.equals("") || quantidade < 0 || valorInvalido) {
            Produto errado = edicao ? new Produto(id, nome, tipo, 0, antigo.getValorCentavos()) : null;
            String mensagem = "Preencha o nome, o tipo e uma quantidade de 0 para cima.";
            if (u.podeDefinirValor()) {
                mensagem = "Preencha o nome, o tipo, uma quantidade de 0 para cima e um valor válido, por exemplo 12,50.";
            }
            enviar(ex, 400, Paginas.formProduto(u, errado, produtoDDSQL.listarTipos(), mensagem));
            return;
        }

        if (edicao) {
            produtoDDSQL.atualizar(id, nome, tipo, quantidade, valor);
            registrar(u, "Editou produto", id, mudancas(antigo, nome, tipo, quantidade, valor));
        } else {
            int novoId = produtoDDSQL.criar(nome, tipo, quantidade, valor);
            registrar(u, "Criou produto", novoId, nome + " (tipo: " + tipo + ", quantidade: " + quantidade
                    + ", valor: " + Dinheiro.formatar(valor) + ")");
        }
        redirecionar(ex, "/produtos");
    }

    // Adiciona (adicionando = true) ou retira (adicionando = false) quantidade do estoque de um produto
    // e grava no histórico quem fez, quanto e como ficou.
    private void moverEstoque(HttpExchange ex, Usuario u, Map<String, String> p, boolean adicionando) throws Exception {
        int id = paraInt(p.get("id"));
        int quantidade = paraInt(p.get("quantidade"));
        Produto antigo = produtoDDSQL.buscar(id);   // como o produto estava ANTES
        if (antigo == null) {
            redirecionar(ex, "/produtos");
            return;
        }

        if (quantidade < 1) {
            avisos.put(u.getId(), new String[] {"erro", "Informe uma quantidade de 1 para cima."});
            redirecionar(ex, "/produtos");
            return;
        }

        int depois;
        if (adicionando) {
            produtoDDSQL.somar(id, quantidade);
            depois = antigo.getQuantidade() + quantidade;
            registrar(u, "Adicionou ao estoque", id, antigo.getNome() + ": adicionou " + quantidade
                    + ", a quantidade passou de " + antigo.getQuantidade() + " para " + depois);
            avisos.put(u.getId(), new String[] {"ok", "Adicionou " + quantidade + " a " + antigo.getNome()
                    + ". Agora tem " + depois + "."});
        } else {
            boolean deuCerto = produtoDDSQL.subtrair(id, quantidade);
            if (!deuCerto) {
                // não tem quantidade suficiente: nada é alterado e o estoque nunca fica negativo
                avisos.put(u.getId(), new String[] {"erro", "Não dá para retirar " + quantidade + " de "
                        + antigo.getNome() + ": só tem " + antigo.getQuantidade() + " no estoque."});
                redirecionar(ex, "/produtos");
                return;
            }
            depois = antigo.getQuantidade() - quantidade;
            registrar(u, "Retirou do estoque", id, antigo.getNome() + ": retirou " + quantidade
                    + ", a quantidade passou de " + antigo.getQuantidade() + " para " + depois);
            avisos.put(u.getId(), new String[] {"ok", "Retirou " + quantidade + " de " + antigo.getNome()
                    + ". Agora tem " + depois + "."});
        }
        redirecionar(ex, "/produtos");
    }

    private void excluirProduto(HttpExchange ex, Usuario u, int id) throws Exception {
        Produto antigo = produtoDDSQL.buscar(id);   // busca ANTES de apagar, para registrar o nome
        if (antigo != null) {
            produtoDDSQL.excluir(id);
            registrar(u, "Excluiu produto", id, antigo.getNome() + " (tipo: " + antigo.getTipo()
                    + ", quantidade: " + antigo.getQuantidade()
                    + ", valor: " + Dinheiro.formatar(antigo.getValorCentavos()) + ")");
        }
        redirecionar(ex, "/produtos");
    }

    // Monta um texto com o que mudou no produto, por exemplo: quantidade de 4 para 10
    private String mudancas(Produto antigo, String nome, String tipo, int quantidade, long valor) {
        String m = "";
        if (!antigo.getNome().equals(nome)) {
            m += "nome: de " + antigo.getNome() + " para " + nome + "; ";
        }
        if (!antigo.getTipo().equals(tipo)) {
            m += "tipo: de " + antigo.getTipo() + " para " + tipo + "; ";
        }
        if (antigo.getQuantidade() != quantidade) {
            m += "quantidade: de " + antigo.getQuantidade() + " para " + quantidade + "; ";
        }
        if (antigo.getValorCentavos() != valor) {
            m += "valor: de " + Dinheiro.formatar(antigo.getValorCentavos()) + " para " + Dinheiro.formatar(valor) + "; ";
        }
        if (m.equals("")) {
            return "nenhuma alteração";
        }
        return m.substring(0, m.length() - 2);   // tira o "; " do final
    }

    // USUÁRIOS

    // Diz se 'logado' pode editar o usuário 'alvo' (a hierarquia).
    // Precisa ter a permissão "Editar usuários". O ADM edita todos (inclusive outros ADM).
    // Os outros só editam quem tem cargo MENOR que o seu, então ninguém edita a si mesmo
    // nem quem tem cargo igual ou maior.
    private boolean podeEditarEsse(Usuario logado, Usuario alvo) {
        if (!logado.podeEditarUsuario()) {
            return false;
        }
        return logado.isAdmin() || logado.getNivel() > alvo.getNivel();
    }

    // Guarda um aviso para aparecer na próxima página aberta pelo usuário
    private void guardarAviso(Usuario u, boolean erro, String texto) {
        avisos.put(u.getId(), new String[] {erro ? "erro" : "ok", texto});
    }

    private void rotasUsuarios(HttpExchange ex, Usuario logado, String caminho,
                               String metodo, Map<String, String> p) throws Exception {

        if (caminho.equals("/usuarios") && metodo.equals("GET")) {
            if (!logado.podeCriarUsuario() && !logado.podeEditarUsuario()) {
                semPermissao(ex, logado);
                return;
            }
            // pega (e apaga) o aviso guardado para este usuário, se existir
            String[] aviso = avisos.remove(logado.getId());
            String msg = aviso == null ? null : aviso[1];
            boolean erro = aviso != null && aviso[0].equals("erro");
            mostrarUsuarios(ex, logado, msg, erro);

        } else if (caminho.equals("/usuarios/novo") && metodo.equals("POST")) {
            if (!logado.podeCriarUsuario()) {
                semPermissao(ex, logado);
                return;
            }
            criarUsuario(ex, logado, p);

        } else if (caminho.equals("/usuarios/editar")) {
            Usuario alvo = usuarioDDSQL.buscarPorId(paraInt(p.get("id")));
            if (alvo == null) {
                redirecionar(ex, "/usuarios");
                return;
            }
            if (!podeEditarEsse(logado, alvo)) {
                semPermissao(ex, logado);
                return;
            }
            if (metodo.equals("POST")) {
                editarUsuario(ex, logado, alvo, p);
            } else {
                enviar(ex, 200, Paginas.editarUsuario(logado, alvo, null, false));
            }

        } else if (caminho.equals("/usuarios/excluir") && metodo.equals("POST")) {
            if (!logado.isAdmin()) {      // só o ADM exclui usuários
                semPermissao(ex, logado);
                return;
            }
            excluirUsuario(ex, logado, paraInt(p.get("id")));

        } else {
            enviar(ex, 404, Paginas.mensagem("Não encontrado", logado, "Página não encontrada."));
        }
    }

    // Cria um usuário novo (feito por quem tem a permissão "Criar usuários")
    private void criarUsuario(HttpExchange ex, Usuario logado, Map<String, String> p) throws Exception {
        String login = p.getOrDefault("login", "").trim();
        String senha = p.getOrDefault("senha", "");
        int nivel = paraInt(p.get("nivel"));

        String erro = null;
        if (login.equals("") || senha.length() < 6) {
            erro = "Informe um nome e uma senha de pelo menos 6 caracteres.";
        } else if (!Nivel.valido(nivel) || nivel > logado.nivelMaximoQuePodeDar()) {
            // hierarquia: o ADM dá qualquer cargo; os outros só dão cargos menores que o seu
            erro = "Você só pode criar usuários de cargo menor que o seu.";
        } else if (usuarioDDSQL.buscarPorLogin(login) != null) {
            erro = "Esse nome já existe.";
        }
        if (erro != null) {
            guardarAviso(logado, true, erro);
            redirecionar(ex, "/usuarios");
            return;
        }

        // Cada permissão só é dada se a pessoa marcou na tela E se quem está criando também tem
        // essa permissão. (A tela já esconde as outras, mas aqui garantimos no servidor também.)
        Usuario novo = new Usuario();
        novo.setLogin(login);
        novo.setNivel(nivel);
        novo.setVer(p.containsKey("ver") && logado.podeVer());
        novo.setCriar(p.containsKey("criar") && logado.podeCriar());
        novo.setEditar(p.containsKey("editar") && logado.podeEditar());
        novo.setExcluir(p.containsKey("excluir") && logado.podeExcluir());
        novo.setDefinirValor(p.containsKey("definirvalor") && logado.podeDefinirValor());
        novo.setAdicionar(p.containsKey("adicionar") && logado.podeAdicionar());
        novo.setRetirar(p.containsKey("retirar") && logado.podeRetirar());
        novo.setCriarUsuario(p.containsKey("criarusuario") && logado.podeCriarUsuario());
        novo.setEditarUsuario(p.containsKey("editarusuario") && logado.podeEditarUsuario());
        novo.setVerHistorico(p.containsKey("historico") && logado.podeVerHistorico());

        String codigo = usuarioDDSQL.criar(novo, senha);
        registrar(logado, "Criou o usuário " + login + " (" + codigo + ")", 0,
                "cargo: " + Nivel.nome(nivel) + "; permissões: " + novo.resumoPermissoes());
        guardarAviso(logado, false, "Usuário \"" + login + "\" criado com o código " + codigo + ".");
        redirecionar(ex, "/usuarios");
    }

    // Decide o valor final de uma permissão ao editar um usuário.
    // Se quem edita TEM essa permissão, vale o que ele marcou na tela.
    // Se NÃO tem, a permissão nem aparece na tela e continua como estava.
    private boolean decidir(boolean quemEditaTem, boolean marcado, boolean atual) {
        return quemEditaTem ? marcado : atual;
    }

    // Salva a edição de um usuário: nome, cargo, senha (se foi preenchida) e permissões
    private void editarUsuario(HttpExchange ex, Usuario logado, Usuario alvo, Map<String, String> p) throws Exception {
        String loginAntigo = alvo.getLogin();
        int nivelAntigo = alvo.getNivel();
        String permissoesAntigas = alvo.resumoPermissoes();

        String novoLogin = p.getOrDefault("login", "").trim();
        String novaSenha = p.getOrDefault("senha", "");
        int novoNivel = paraInt(p.get("nivel"));
        if (alvo.getId() == logado.getId()) {
            novoNivel = nivelAntigo;      // o ADM não muda o próprio cargo
        }

        // validações: se algo estiver errado, mostra a tela de edição de novo com o erro
        Usuario outro = novoLogin.equals("") ? null : usuarioDDSQL.buscarPorLogin(novoLogin);
        String erro = null;
        if (novoLogin.equals("")) {
            erro = "O nome não pode ficar vazio.";
        } else if (outro != null && outro.getId() != alvo.getId()) {
            erro = "Já existe outro usuário com esse nome.";
        } else if (!novaSenha.equals("") && novaSenha.length() < 6) {
            erro = "A nova senha precisa ter pelo menos 6 caracteres.";
        } else if (!Nivel.valido(novoNivel) || novoNivel > logado.nivelMaximoQuePodeDar()) {
            erro = "Você só pode dar cargos menores que o seu.";
        } else if (alvo.isAdmin() && novoNivel != Nivel.ADM && usuarioDDSQL.contarAdmins() <= 1) {
            erro = "Precisa existir pelo menos um ADM.";
        }
        if (erro != null) {
            enviar(ex, 400, Paginas.editarUsuario(logado, alvo, erro, true));
            return;
        }

        // aplica as mudanças (o cargo por último, porque o ADM tem todas as permissões)
        alvo.setLogin(novoLogin);
        alvo.setVer(decidir(logado.podeVer(), p.containsKey("ver"), alvo.podeVer()));
        alvo.setCriar(decidir(logado.podeCriar(), p.containsKey("criar"), alvo.podeCriar()));
        alvo.setEditar(decidir(logado.podeEditar(), p.containsKey("editar"), alvo.podeEditar()));
        alvo.setExcluir(decidir(logado.podeExcluir(), p.containsKey("excluir"), alvo.podeExcluir()));
        alvo.setDefinirValor(decidir(logado.podeDefinirValor(), p.containsKey("definirvalor"), alvo.podeDefinirValor()));
        alvo.setAdicionar(decidir(logado.podeAdicionar(), p.containsKey("adicionar"), alvo.podeAdicionar()));
        alvo.setRetirar(decidir(logado.podeRetirar(), p.containsKey("retirar"), alvo.podeRetirar()));
        alvo.setCriarUsuario(decidir(logado.podeCriarUsuario(), p.containsKey("criarusuario"), alvo.podeCriarUsuario()));
        alvo.setEditarUsuario(decidir(logado.podeEditarUsuario(), p.containsKey("editarusuario"), alvo.podeEditarUsuario()));
        alvo.setVerHistorico(decidir(logado.podeVerHistorico(), p.containsKey("historico"), alvo.podeVerHistorico()));
        alvo.setNivel(novoNivel);

        usuarioDDSQL.atualizar(alvo);     // grava e monta o código de novo (a sigla acompanha o cargo)
        if (!novaSenha.equals("")) {
            usuarioDDSQL.trocarSenha(alvo.getId(), novaSenha);   // a senha NÃO vai para o histórico
        }

        // registra no histórico o que mudou
        String detalhes = "";
        if (!loginAntigo.equals(alvo.getLogin())) {
            detalhes += "nome: de " + loginAntigo + " para " + alvo.getLogin() + "; ";
        }
        if (nivelAntigo != alvo.getNivel()) {
            detalhes += "cargo: de " + Nivel.nome(nivelAntigo) + " para " + Nivel.nome(alvo.getNivel()) + "; ";
        }
        if (!permissoesAntigas.equals(alvo.resumoPermissoes())) {
            detalhes += "permissões agora: " + alvo.resumoPermissoes() + "; ";
        }
        if (!novaSenha.equals("")) {
            detalhes += "senha trocada; ";
        }
        detalhes = detalhes.equals("") ? "nenhuma alteração" : detalhes.substring(0, detalhes.length() - 2);

        registrar(logado, "Editou o usuário " + loginAntigo + " (" + alvo.getCodigo() + ")", 0, detalhes);
        guardarAviso(logado, false, "Usuário \"" + alvo.getLogin() + "\" atualizado. Código: " + alvo.getCodigo() + ".");
        redirecionar(ex, "/usuarios");
    }

    // Exclui um usuário (só o ADM). Não deixa excluir a si mesmo nem o último ADM.
    private void excluirUsuario(HttpExchange ex, Usuario logado, int id) throws Exception {
        Usuario alvo = usuarioDDSQL.buscarPorId(id);
        if (alvo == null) {
            guardarAviso(logado, true, "Usuário não encontrado.");
        } else if (id == logado.getId()) {
            guardarAviso(logado, true, "Você não pode excluir o seu próprio usuário.");
        } else if (alvo.isAdmin() && usuarioDDSQL.contarAdmins() <= 1) {
            guardarAviso(logado, true, "Precisa existir pelo menos um ADM.");
        } else {
            usuarioDDSQL.excluir(id);
            sessoes.values().remove(id);   // desloga o usuário excluído
            registrar(logado, "Excluiu o usuário " + alvo.getLogin() + " (" + alvo.getCodigo() + ")", 0,
                    "cargo: " + Nivel.nome(alvo.getNivel()));
            guardarAviso(logado, false, "Usuário \"" + alvo.getLogin() + "\" excluído.");
        }
        redirecionar(ex, "/usuarios");
    }

    // Mostra a página de usuários. A lista só tem quem a pessoa pode editar (a hierarquia).
    private void mostrarUsuarios(HttpExchange ex, Usuario logado, String msg, boolean erro) throws Exception {
        List<Usuario> lista = new ArrayList<>();
        if (logado.podeEditarUsuario()) {
            for (Usuario x : usuarioDDSQL.listar()) {
                if (podeEditarEsse(logado, x)) {
                    lista.add(x);
                }
            }
        }
        enviar(ex, 200, Paginas.usuarios(logado, lista, msg, erro));
    }

    // HISTÓRICO
    // Grava no histórico uma ação feita por um usuário.
    private void registrar(Usuario u, String acao, int produtoId, String detalhes) throws Exception {
        historicoDDSQL.registrar(u.getLogin(), u.getCodigo(), acao, produtoId, detalhes);
    }

    // FERRAMENTAS
    private void semPermissao(HttpExchange ex, Usuario u) throws IOException {
        enviar(ex, 403, Paginas.mensagem("Sem permissão", u, "Você não tem permissão para isso. Fale com um ADM."));
    }

    // Descobre quem está logado: lê o cookie, acha o id e busca o usuário no banco.
    // Buscamos no banco a cada pedido, assim mudanças de permissão valem na hora.
    private Usuario usuarioLogado(HttpExchange ex) throws Exception {
        String token = lerToken(ex);
        if (token == null || !sessoes.containsKey(token)) {
            return null;
        }
        Usuario u = usuarioDDSQL.buscarPorId(sessoes.get(token));
        if (u == null) {
            sessoes.remove(token);   // usuário foi excluído
        }
        return u;
    }

    // Lê o valor do cookie "SESSAO" enviado pelo navegador
    private String lerToken(HttpExchange ex) {
        String cookies = ex.getRequestHeaders().getFirst("Cookie");
        if (cookies == null) {
            return null;
        }
        for (String parte : cookies.split(";")) {
            parte = parte.trim();
            if (parte.startsWith("SESSAO=")) {
                return parte.substring("SESSAO=".length());
            }
        }
        return null;
    }

    // Junta os dados da URL (por exemplo q=abc) e do formulário (POST) num único Map de nome e valor
    private Map<String, String> lerParametros(HttpExchange ex) throws IOException {
        Map<String, String> mapa = new HashMap<>();
        String url = ex.getRequestURI().getRawQuery();
        if (url != null) {
            separar(url, mapa);
        }
        if (ex.getRequestMethod().equals("POST")) {
            InputStream in = ex.getRequestBody();
            String corpo = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            separar(corpo, mapa);
        }
        return mapa;
    }

    // Transforma "nome=Parafuso&tipo=Ferragem" em pares de nome e valor
    private void separar(String texto, Map<String, String> mapa) {
        for (String par : texto.split("&")) {
            if (par.equals("")) {
                continue;
            }
            String[] partes = par.split("=", 2);
            String chave = URLDecoder.decode(partes[0], StandardCharsets.UTF_8);
            String valor = partes.length > 1 ? URLDecoder.decode(partes[1], StandardCharsets.UTF_8) : "";
            mapa.put(chave, valor);
        }
    }

    // Converte texto em número. Se não der (ou for nulo), devolve -1.
    private int paraInt(String texto) {
        try {
            return Integer.parseInt(texto.trim());
        } catch (Exception e) {
            return -1;
        }
    }

    // Envia uma página HTML para o navegador
    private void enviar(HttpExchange ex, int codigo, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        ex.sendResponseHeaders(codigo, bytes.length);
        ex.getResponseBody().write(bytes);
    }

    // Manda o navegador ir para outro endereço
    private void redirecionar(HttpExchange ex, String destino) throws IOException {
        ex.getResponseHeaders().set("Location", destino);
        ex.sendResponseHeaders(303, -1);
    }

    // Envia o arquivo de estilo (o visual do site)
    private void enviarCss(HttpExchange ex) throws IOException {
        InputStream in = getClass().getResourceAsStream("/static/estilo.css");
        byte[] bytes = in.readAllBytes();
        ex.getResponseHeaders().set("Content-Type", "text/css; charset=UTF-8");
        ex.sendResponseHeaders(200, bytes.length);
        ex.getResponseBody().write(bytes);
    }
}
