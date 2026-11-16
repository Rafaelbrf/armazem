#Projeto pessoal de estudo, feito durante o curso de Análise e Desenvolvimento de Sistemas (UEPB).

Sistema web de controle de estoque que junta Programação Orientada a Objetos, banco de dados SQL e desenvolvimento web (front-end e back-end).

Tecnologias: Java (POO), SQL (SQLite), HTML e CSS, Maven.

O que o sistema faz:
- Cadastro de produtos com tipo, quantidade e valor
- Entrada e saída de estoque
- Login com cargos (Operador, Supervisor, Gerente e ADM) e permissões
- Código único para cada usuário
- Histórico de quem fez cada ação
- Três bancos de dados separados: produtos, usuários e histórico

Projeto em desenvolvimento, usado para praticar e aprender.

# Armazém

Sistema web de controle de armazém feito em **Java (POO)** com **HTML + CSS** e banco de dados **SQL (SQLite)**.
Tudo acontece pelo site: criar, editar e excluir produtos, criar usuários e definir permissões sem abrir o código.

## Funcionalidades

- **Produtos**: ID automático, nome, tipo, quantidade e **valor** (em reais); busca por nome e filtro por tipo.
  Na primeira execução o sistema já cadastra 4 produtos de exemplo, com valor.
- **Valor dos produtos**: só quem tem a permissão **Definir valor dos produtos** coloca ou muda o valor.
  Quem não tem a permissão não vê o campo e, mesmo tentando enviar na mão, o valor não muda.
- **Adicionar e retirar estoque**: cada produto tem um campo de quantidade e os botões **+ Adicionar** e
  **- Retirar**. A retirada nunca deixa o estoque negativo. Toda entrada e saída fica salva no histórico.
- **Login** com senha protegida (PBKDF2 + salt) e sessão por cookie.
- **Três bancos de dados SQL separados** (pasta `dados/`):
  - `produtos.db` produtos (cada um com seu ID e valor)
  - `usuarios.db` logins, cargos, códigos e permissões
  - `historico.db` quem fez o quê, quando e o ID do produto envolvido
- **Hierarquia de cargos**: Operador, Supervisor, Gerente e **ADM** (o maior). Pode existir **mais de um ADM**.
  Quem tem cargo maior manda em quem tem cargo menor: só dá cargos menores que o seu e só edita quem tem cargo
  menor que o seu. O ADM edita todos. Nunca fica sem nenhum ADM, e ninguém exclui a si mesmo.
- **Código (ID) do usuário**: sigla do cargo mais o número de entrada, por exemplo `GER-0004`.
  O número de entrada nunca se repete (nem quando um usuário é excluído), então o código nunca se repete.
  O banco ainda tem um índice único que recusa qualquer código repetido. Os nomes também não se repetem
  (Ana e ana contam como o mesmo nome). Se o usuário muda de cargo, a sigla muda e o número fica igual.
  O histórico guarda o código de quem fez cada ação.
- **Editar usuário**: na aba **Usuários**, o botão **Editar usuário** abre uma tela com tudo: nome, cargo,
  nova senha (opcional) e todas as permissões.
- **Permissões** (10, marcadas na edição do usuário): ver produtos, criar produtos, editar produtos,
  excluir produtos, definir valor dos produtos, adicionar ao estoque, retirar do estoque, criar usuários,
  editar usuários e ver histórico. O ADM tem todas. Quem cria ou edita usuários só consegue dar as
  permissões que ele mesmo tem. Só o ADM exclui usuários.
- **Histórico**: página que lista entradas e saídas do sistema, criação, edição e exclusão de produtos
  (com o que mudou, inclusive o valor), entradas e saídas de estoque, criação, edição e exclusão de usuários
  e tentativas de login que falharam. Dá para filtrar pelo ID de um produto. Não tem botão de apagar.
- Quem não tem permissão **nem vê** o botão e, se tentar acessar o endereço direto, recebe "Sem permissão".
  As mudanças de permissão valem na hora, sem precisar relogar.

## Como rodar

Requisitos: **Java 17+**

Abra <http://localhost:8080>.

Primeiro acesso (criado automaticamente na 1ª execução):

| Login | Senha |
|-------|-------|
| `admin` | `admin123` |

>  Entre na aba **Usuários** e troque a senha do `admin` logo no primeiro acesso.

## Estrutura (POO)

Todo o código tem comentários explicando o que cada parte faz.

```
src/main/java/armazem/
├── Main.java                 # começa aqui: cria tabelas, ADM inicial, produtos de exemplo e liga o servidor
├── model/                    # classes que representam os "objetos" do sistema
│   ├── Produto.java          # id, nome, tipo, quantidade e valor
│   ├── Usuario.java          # nome, senha (hash), código, cargo e as 10 permissões
│   ├── Nivel.java            # os cargos e a regra que monta o código (ID) do usuário
│   ├── Dinheiro.java         # converte e mostra valores em reais
│   └── Registro.java         # uma linha do histórico
├── ddsql/                    # DDSQL = "Dados do SQL": classes que conversam com o banco
│   ├── Conexao.java          # abre os três bancos e cria as tabelas
│   ├── ProdutoDDSQL.java
│   ├── UsuarioDDSQL.java
│   └── HistoricoDDSQL.java
├── seguranca/Senha.java      # hash da senha e token de login
└── web/
    ├── Servidor.java         # rotas do site, checagem de permissões e gravação do histórico
    └── Paginas.java          # monta o HTML de cada página
src/main/resources/static/estilo.css   # visual do site
```

## Subindo no Git

```bash
git init
git add .
git commit -m "Sistema de armazém em Java"
git branch -M main
git remote add origin git https://github.com/Rafaelbrf/estoque.git
git push -u origin main
```

O `.gitignore` já ignora a pasta `dados/` (os bancos com usuários, senhas e histórico **não** vão para o GitHub) e a pasta `target/`.

## Para evoluir depois

- Histórico de entradas e saídas de estoque.
- Proteção CSRF e HTTPS (obrigatório se for publicar na internet).
- Trocar o servidor embutido por Spring Boot.
