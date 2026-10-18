package com.teste.game.telas;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.Scaling;
import com.teste.game.rede.GameSocket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BookMenuUI {

    // Diametro do botao redondo das laterais + "aba" reta que liga o circulo
    // ao painel (ver prints/tela desejada.png).
    private static final float BOTAO_SIZE = 74f;
    private static final float BOTAO_ABA = 8f;
    private static final float COLUNA_LARGURA = BOTAO_SIZE + BOTAO_ABA;
    private static final float JANELA_LARGURA = 520f;
    private static final float JANELA_ALTURA = 410f;
    private static final int COLUNAS_INVENTARIO = 6;
    private static final float COLUNA_BAG = 133f;
    private static final float TAM_BOTAO_ACAO = 52f;

    private final Table root = new Table();
    private final Map<String, Button> botoes = new LinkedHashMap<>();
    private final Label tituloSecao;
    private final Skin skin;
    private final TextureAtlas atlas;
    private final GameSocket socket;
    private final String classeJogador;
    private final Table mainWindow = new Table();
    private final Table bagPage = new Table();
    private final Table equipPage = new Table();
    private final Table skillsPage = new Table();
    private final Table spellsPage = new Table();
    private final Table vanityPage = new Table();
    private final Table friendsPage = new Table();
    private final Table inventoryGrid = new Table();
    private final Table bagDetalhes = new Table();
    private final Label capacityLabel;
    private BarraUso barraCapBag;
    // No celular o livro ja' e' ampliado (organizer 1.2x) e a fonte "hud"
    // sai grande demais nos stats/capacity - reduz so' esses textos.
    private static final float FONTE_STATS = (Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android
        || Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.iOS) ? 0.75f : 1f;
    private final Label actionStatus;
    private final Map<String, Label> currencyLabels = new LinkedHashMap<>();
    private final List<InventoryItem> inventoryItems = new ArrayList<>();
    private Button favoriteButton;
    private Button trashButton;
    private Table actionButtons;
    private Table deleteConfirmation;
    private Table actionBar;
    // Modo de exclusao: clicar nos itens marca/desmarca (ids aqui) e
    // Confirm manda todos de uma vez pro servidor (delete_items).
    private boolean modoExclusao = false;
    private final java.util.Set<String> idsParaExcluir = new java.util.LinkedHashSet<>();
    private com.badlogic.gdx.scenes.scene2d.ui.Cell<Label> celulaStatus;
    private int selectedItem = -1;
    private String selectedInstanceId = "";
    private long currencyTotal;
    private float capacityUsed;
    private float capacityMaximum;
    private String secaoAtual = "Bag";

    // --- Equip ---
    // Grade 3x3 da coluna esquerda (null = celula vazia), igual prints/tela desejada.png.
    private static final String[][] SLOTS_EQUIP = {
        {null, "Helm", "Necklace"}, {"MainHand", "Chest", "Hand"}, {"Gloves", "Boots", "Ring"}
    };
    private static final float SLOT_EQUIP = 54f;
    private static final float COLUNA_EQUIP = 180f;
    private static final float ALTURA_GRADE_EQUIP = 216f;
    private static final int COLUNAS_EQUIP = 5;
    private final Map<String, Button> equipSlotButtons = new LinkedHashMap<>();
    private final Map<String, String> equippedItemPaths = new LinkedHashMap<>();
    private final Map<String, Integer> equippedQuantidades = new HashMap<>();
    private final List<Integer> equipCandidatos = new ArrayList<>();
    private final Table equipItemGrid = new Table();
    private final Table equipDetalhesEsquerda = new Table();
    private final Table equipDetalhesDireita = new Table();
    private Table equipColunaEsquerda;
    // Detalhes do equip com rolagem: descricao comprida (ex: set) rola em vez de vazar a janela.
    private ScrollPane equipScrollEsquerda, equipScrollDireita;
    private Table equipSlotsTabela;
    private Table equipColunaDireita;
    private Table equipPainelGrade;
    private TextButton equipButton;
    private TextButton.TextButtonStyle estiloEquipar, estiloEquiparBloqueado;
    private TextButton unequipButton;
    private String selectedSlot = "";
    private int selectedEquipCandidate = -1;

    // --- Skills ---
    private BarraSkill barraNivel;
    private BarraSkill barraPrincipal;
    private BarraSkill barraDefesa;
    private BarraSkill barraFome;
    private final Map<String, Label> valoresStatus = new LinkedHashMap<>();
    private JsonValue ultimasSkills;
    private int nivelAtual = 1;
    private int expAtual = 0;
    private int killsAtual = 0;
    private static final float LARGURA_BARRA_SKILL = 285f;
    private static final float ALTURA_BARRA_SKILL = 24f;
    private static final float LARGURA_NOME_SKILL = 90f;
    private static final float TAMANHO_ICONE_SKILL = 24f;
    private static final float TAMANHO_ICONE_STATUS = 20f;
    private static final float LARGURA_ROTULO_STATUS = 124f;
    private static final float ALTURA_TOPO_SKILLS = 160f;

    public BookMenuUI(Stage stage, Skin skin, TextureAtlas atlas, GameSocket socket, String classeJogador) {
        this.skin = skin;
        this.atlas = atlas;
        this.socket = socket;
        this.classeJogador = classeJogador == null ? "Knight" : classeJogador;
        skin.add("book-button", UiSkin.retangulo(
            new Color(0.08f, 0.09f, 0.10f, 0.98f), UiSkin.COR_BORDA_PAINEL, 1),
            com.badlogic.gdx.scenes.scene2d.utils.Drawable.class);
        skin.add("book-button-hover", UiSkin.retangulo(
            new Color(0.12f, 0.22f, 0.22f, 1f), new Color(0.25f, 0.7f, 0.62f, 1f), 1),
            com.badlogic.gdx.scenes.scene2d.utils.Drawable.class);
        skin.add("book-button-selected", UiSkin.retangulo(
            new Color(0.08f, 0.38f, 0.32f, 1f), new Color(0.35f, 0.92f, 0.72f, 1f), 2),
            com.badlogic.gdx.scenes.scene2d.utils.Drawable.class);
        skin.add("bag-slot", UiSkin.retangulo(
            new Color(0.15f, 0.15f, 0.15f, 1f), new Color(0.21f, 0.21f, 0.21f, 1f), 2),
            com.badlogic.gdx.scenes.scene2d.utils.Drawable.class);
        skin.add("bag-slot-hover", UiSkin.retangulo(
            new Color(0.18f, 0.18f, 0.18f, 1f), new Color(0.32f, 0.32f, 0.32f, 1f), 2),
            com.badlogic.gdx.scenes.scene2d.utils.Drawable.class);
        skin.add("bag-slot-selected", UiSkin.retangulo(
            new Color(0.2f, 0.2f, 0.2f, 1f), new Color(0.95f, 0.65f, 0.24f, 1f), 2),
            com.badlogic.gdx.scenes.scene2d.utils.Drawable.class);
        skin.add("bag-slot-delete", UiSkin.retangulo(
            new Color(0.24f, 0.05f, 0.05f, 1f), new Color(0.9f, 0.35f, 0.35f, 1f), 2),
            com.badlogic.gdx.scenes.scene2d.utils.Drawable.class);
        skin.add("bag-detail", UiSkin.retangulo(
            new Color(0.025f, 0.028f, 0.03f, 1f), new Color(0.12f, 0.13f, 0.14f, 1f), 1),
            com.badlogic.gdx.scenes.scene2d.utils.Drawable.class);

        Table organizer = new Table() {
            @Override
            public void layout() {
                super.layout();
                setOrigin(getWidth() / 2f, getHeight() / 2f);
            }
        };
        // Celular: o X (fechar) vai pro topo da coluna da direita (mais facil
        // de alcancar), e Spells passa pro fim da esquerda. PC: como era.
        boolean xEmCima = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android
            || Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.iOS;
        Table leftTab = criarColuna(atlas, xEmCima ? new String[][]{
            {"Party", "ui/buttons/PartyBtn"},
            {"Friends", "ui/buttons/FriendsBtn"},
            {"Rank", "ui/buttons/RankBtn"},
            {"Bestiary", "sheet/r2_c13"},
            {"Spells", "ui/buttons/SpellsBtn"}
        } : new String[][]{
            {"Party", "ui/buttons/PartyBtn"},
            {"Friends", "ui/buttons/FriendsBtn"},
            {"Rank", "ui/buttons/RankBtn"},
            {"Bestiary", "sheet/r2_c13"},
            {"Exit", "ui/buttons/CloseBtn"}
        }, true);
        Table rightTab = criarColuna(atlas, xEmCima ? new String[][]{
            {"Exit", "ui/buttons/CloseBtn"},
            {"Equip", "ui/buttons/EquipMenuBtn"},
            {"Bag", "ui/buttons/InventoryBtn"},
            {"Skills", "ui/buttons/SkillsBtn"},
            {"Vanity", "ui/buttons/SkinsBtn"}
        } : new String[][]{
            {"Equip", "ui/buttons/EquipMenuBtn"},
            {"Bag", "ui/buttons/InventoryBtn"},
            {"Skills", "ui/buttons/SkillsBtn"},
            {"Vanity", "ui/buttons/SkinsBtn"},
            {"Spells", "ui/buttons/SpellsBtn"}
        }, false);

        mainWindow.setBackground(UiSkin.retangulo(
            new Color(0.125f, 0.125f, 0.125f, 0.98f), UiSkin.COR_BORDA_PAINEL, 1));
        mainWindow.pad(14);
        mainWindow.top().left();
        tituloSecao = new LabelPixel("Bag", skin.get("subtitulo", Label.LabelStyle.class));
        capacityLabel = new LabelPixel("Capacity 0/0", skin.get("hud", Label.LabelStyle.class));
        actionStatus = new LabelPixel("", skin.get("hud", Label.LabelStyle.class));
        construirPaginaBag(skin);
        construirPaginaEquip(skin);
        atualizarEquipados(null);
        construirPaginaSkills(skin);
        construirPaginaVanity(skin);
        construirPaginaFriends(skin);
        construirPaginaParty(skin);
        construirPaginasTrade(skin);
        construirPaginaBanco(skin);
        construirPaginaLoja();
        colunaEsquerdaAbas = leftTab;
        colunaDireitaAbas = rightTab;
        mainWindow.add(bagPage).grow();

        organizer.add(leftTab).width(COLUNA_LARGURA).height(JANELA_ALTURA);
        celulaJanela = organizer.add(mainWindow).width(JANELA_LARGURA).height(JANELA_ALTURA);
        organizer.add(rightTab).width(COLUNA_LARGURA).height(JANELA_ALTURA);
        organizer.setTransform(true);
        boolean mobile = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android
            || Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.iOS;
        organizer.setScale(mobile ? 1.2f : 1f);

        root.setFillParent(true);
        root.center();
        celulaOrganizer = root.add(organizer).width(COLUNA_LARGURA * 2f + JANELA_LARGURA)
            .height(JANELA_ALTURA);
        // Descricao comprida (ex: set do item): a coluna da esquerda e a
        // janela crescem pra caber, em vez de cortar o texto.
        root.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.forever(
            com.badlogic.gdx.scenes.scene2d.actions.Actions.run(this::ajustarLarguraDetalhes)));
        stage.addActor(root);
        registrarAtalhos(stage);
        root.setVisible(false);
        selecionarSecao(secaoAtual);
    }

    private Table criarColuna(TextureAtlas atlas, String[][] secoes, boolean esquerda) {
        Table coluna = new Table();
        coluna.top();
        float espaco = (JANELA_ALTURA - BOTAO_SIZE * secoes.length) / Math.max(1, secoes.length - 1);
        for (int i = 0; i < secoes.length; i++) {
            String[] secao = secoes[i];
            String nome = secao[0];
            Color cor = corDaSecao(nome);
            Button.ButtonStyle estilo = new Button.ButtonStyle();
            estilo.up = botaoRedondo(cor.cpy().mul(0.22f, 0.22f, 0.22f, 1f),
                cor.cpy().mul(0.1f, 0.1f, 0.1f, 1f), cor.cpy().mul(0.3f, 0.3f, 0.3f, 1f), esquerda);
            estilo.over = botaoRedondo(cor.cpy().mul(0.32f, 0.32f, 0.32f, 1f),
                cor.cpy().mul(0.16f, 0.16f, 0.16f, 1f), cor.cpy().mul(0.6f, 0.6f, 0.6f, 1f), esquerda);
            estilo.down = botaoRedondo(cor.cpy().mul(0.45f, 0.45f, 0.45f, 1f),
                cor.cpy().mul(0.22f, 0.22f, 0.22f, 1f), cor, esquerda);
            estilo.checked = botaoRedondo(cor.cpy().mul(0.4f, 0.4f, 0.4f, 1f),
                cor.cpy().mul(0.2f, 0.2f, 0.2f, 1f), cor, esquerda);
            estilo.checkedOver = estilo.checked;

            Button botao = new Button(estilo);
            // setChecked() feito pelo codigo (selecionarSecao) NAO pode virar
            // clique: o Exit ficava marcado depois de fechar o livro e, ao
            // trocar de aba, desmarcar ele disparava acionarBotao("Exit")
            // - qualquer botao fechava o livro.
            botao.setProgrammaticChangeEvents(false);
            Image icone = new Image(new TextureRegionDrawable(atlas.findRegion(secao[1])));
            icone.setScaling(Scaling.fit);
            // Centraliza o icone no circulo (a aba fica do lado do painel).
            botao.add(icone).size(BOTAO_SIZE * 0.55f)
                .padLeft(esquerda ? 0f : BOTAO_ABA).padRight(esquerda ? BOTAO_ABA : 0f);
            botao.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                    acionarBotao(nome);
                }
            });
            botoes.put(nome, botao);
            coluna.add(botao).width(COLUNA_LARGURA).height(BOTAO_SIZE)
                .padBottom(i < secoes.length - 1 ? espaco : 0f).row();
        }
        return coluna;
    }

    /** Circulo com uma aba reta do lado do painel: borda, anel e miolo mais escuro. */
    private static TextureRegionDrawable botaoRedondo(Color anel, Color miolo, Color borda, boolean esquerda) {
        int altura = 128;
        int aba = Math.round(altura * BOTAO_ABA / BOTAO_SIZE);
        int largura = altura + aba;
        int raio = altura / 2;
        int centroX = esquerda ? raio : largura - raio;
        int abaX = esquerda ? raio : 0;
        int abaLargura = largura - raio;
        int espessura = 3;
        Pixmap pm = new Pixmap(largura, altura, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(0, 0, 0, 0);
        pm.fill();
        pm.setColor(borda);
        pm.fillCircle(centroX, raio, raio - 1);
        pm.fillRectangle(abaX, 0, abaLargura, altura);
        pm.setColor(anel);
        pm.fillCircle(centroX, raio, raio - 1 - espessura);
        pm.fillRectangle(esquerda ? abaX : 0, espessura, abaLargura, altura - 2 * espessura);
        pm.setColor(miolo);
        pm.fillCircle(centroX, raio, Math.round(raio * 0.8f));
        Texture tex = new Texture(pm);
        tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pm.dispose();
        return new TextureRegionDrawable(new com.badlogic.gdx.graphics.g2d.TextureRegion(tex));
    }

    private void construirPaginaBag(Skin skin) {
        bagPage.top().left();
        inventoryGrid.top().left();
        bagDetalhes.top().left();

        // Favoritar/lixeira no rodape da coluna esquerda; no modo de exclusao
        // viram cancelar (Negate) / confirmar (Confirm).
        actionButtons = new Table();
        favoriteButton = criarBotaoAcao("ui/Star", new Color(0.42f, 0.35f, 0.04f, 1f),
            new Color(0.72f, 0.6f, 0.1f, 1f), () -> alternarFavorito());
        trashButton = criarBotaoAcao("ui/Trash", new Color(0.3f, 0.05f, 0.05f, 1f),
            new Color(0.52f, 0.1f, 0.1f, 1f), () -> iniciarExclusao());
        actionButtons.add(favoriteButton).size(TAM_BOTAO_ACAO).padRight(10);
        actionButtons.add(trashButton).size(TAM_BOTAO_ACAO);
        deleteConfirmation = new Table();
        Button cancelar = criarBotaoAcao("ui/Negate", new Color(0.26f, 0.04f, 0.04f, 1f),
            new Color(0.42f, 0.08f, 0.08f, 1f), () -> cancelarExclusao());
        Button confirmar = criarBotaoAcao("ui/Confirm", new Color(0.04f, 0.2f, 0.04f, 1f),
            new Color(0.08f, 0.36f, 0.08f, 1f), () -> confirmarExclusao());
        deleteConfirmation.add(cancelar).size(TAM_BOTAO_ACAO).padRight(10);
        deleteConfirmation.add(confirmar).size(TAM_BOTAO_ACAO);
        deleteConfirmation.setVisible(false);
        Stack botoesAcao = new Stack(actionButtons, deleteConfirmation);

        escalarFonte(actionStatus, 0.6f);
        actionStatus.setAlignment(Align.center);
        actionBar = new Table();
        actionBar.add(botoesAcao).center();

        Table colunaEsquerda = new Table();
        colunaEsquerda.top().left();
        colunaEsquerda.setBackground(UiSkin.retangulo(
            new Color(0.08f, 0.08f, 0.08f, 1f), new Color(0.35f, 0.35f, 0.35f, 1f), 1));
        colunaEsquerda.add(bagDetalhes).grow().top().left().pad(8, 8, 8, 8).row();
        // "N Items Selected" - so' ocupa altura no modo de exclusao.
        celulaStatus = colunaEsquerda.add(actionStatus).growX().center().height(0f).padBottom(0f);
        colunaEsquerda.row();
        colunaEsquerda.add(separadorEquip()).growX().height(1).row();
        colunaEsquerda.add(actionBar).growX().pad(4, 4, 5, 4);

        ScrollPane scroll = new ScrollPane(inventoryGrid, skin);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        scroll.setOverscroll(false, false);
        scroll.setFlickScroll(true);

        escalarFonte(capacityLabel, 0.8f * FONTE_STATS);
        capacityLabel.setColor(new Color(0.65f, 0.65f, 0.65f, 1f));

        Table moedas = new Table();
        moedas.left();
        String[] tiposMoeda = {"Copper", "Silver", "Gold", "Platinum"};
        for (String tipo : tiposMoeda) {
            Image icone = new Image(new TextureRegionDrawable(atlas.findRegion("ui/currency/" + tipo)));
            icone.setScaling(Scaling.fit);
            // Mesma fonte das descricoes (a "hud" reduzida ficava borrada do lado delas).
            Label quantidade = new LabelPixel("0", estiloNitido(TAM_TEXTO_RODAPE));
            quantidade.setColor(corMoeda(tipo));
            moedas.add(icone).size(24).padRight(4);
            moedas.add(quantidade).left().padRight(10);
            currencyLabels.put(tipo, quantidade);
        }

        Table colunaDireita = new Table();
        colunaDireita.top().left();
        colunaDireita.setBackground(UiSkin.retangulo(
            new Color(0.165f, 0.165f, 0.165f, 1f), new Color(0.165f, 0.165f, 0.165f, 1f), 1));
        colunaDireita.add(scroll).grow().pad(12, 12, 6, 12).row();
        barraCapBag = new BarraUso();
        capacityLabel.setStyle(estiloNitido(TAM_TEXTO_CAP));
        capacityLabel.setFontScale(1f);
        capacityLabel.setColor(Color.WHITE);
        capacityLabel.setAlignment(Align.center);
        Table textoCap = new Table();
        textoCap.add(capacityLabel).center();
        colunaDireita.add(new Stack(barraCapBag, textoCap)).growX().height(24).pad(0, 12, 8, 12).row();
        colunaDireita.add(moedas).left().padLeft(8).padBottom(10);

        bagPage.add(colunaEsquerda).width(COLUNA_BAG).growY();
        bagPage.add(colunaDireita).grow();
        atualizarCapacidade(0f, 100f);
        atualizarDetalhes(null);
        atualizarGrade();
    }

    // ===================== EQUIP =====================

    /** Dados de um item vindos do ITEM_DB do servidor (ver carregarItemDb). */
    private static final class EquipStats {
        final String nome, tipo, slot;
        final int reqLevel;
        final String reqClass;
        final int bonusDamage, defense, armour, stamina, mana, fourthStatValue, manaCost, fullness;
        final int healHp, healMp; // pocao: quanto cura (o que aparece)
        final String fourthStatType, icone;
        final float cap; // peso de 1 unidade (servidor.py::ITEM_DB "cap")
        final int regenHp, regenMp, regenSeg, preco; // comida com buff de regen; preco em copper
        final int precoVendaFixo; // material: quanto o NPC paga (servidor "sell_price"); 0 = metade do preco
        final boolean usavel; // scroll etc: botao Use na Bag
        final boolean empilhavel;
        final String magia;    // id da magia (servidor.py "spell"); null = item normal
        final int magiaMana, magiaCd; // custo, cooldown (s)
        final float magiaDano;        // % do dano base por golpe (ex: 21.5)
        final int magiaGolpes;        // Arrow Barrage: 3 flechas
        final String magiaDescricao;
        final boolean magiaArea; // true = dano em area; false = um alvo so'
        final int sangraTicks;               // ticks do DoT (Heavy Cut: sangra; Small Vines: veneno)
        final float sangraPct, sangraCada;   // % por tick e segundos entre eles
        final String magiaDot;               // "bleed" ou "poison"
        final float magiaLento;              // segundos de lentidao no mob
        final String magiaSlot; // cor do slot: "orange" (padrao) ou "red"
        final String magiaExpSkill; // skill que a magia treina (magic/melee/distance/musicality)
        final int magiaExp;         // golpes na skill por uso

        final boolean treino;  // arma de treino (servidor.py::dano_arma_treino)
        final int economiaFlecha; // % de chance de nao gastar flecha
        final float velocidadeAtaque; // "atk_speed" (Basic Dagger = 1.1); 1 = normal
        final String efeitoAtaque, projetilAtaque; // "atk_hit"/"atk_proj": no lugar do da classe (Ruby Staff = fogo)
        final String conjunto; // set (ex: "Outlander"); null = nenhum
        final int conjuntoXp;  // % de XP extra com o set completo

        EquipStats(JsonValue d) {
            this.magia = d.getString("spell", null);
            this.magiaMana = d.getInt("spell_mana", 0);
            this.magiaCd = d.getInt("spell_cd", 0);
            this.magiaDano = d.getFloat("spell_dmg", 0f);
            this.magiaGolpes = Math.max(1, d.getInt("spell_hits", 1));
            this.magiaDescricao = d.getString("spell_desc", "");
            this.magiaArea = d.getBoolean("spell_area", false);
            this.sangraTicks = d.getInt("spell_bleed_ticks", 0);
            this.sangraPct = d.getFloat("spell_bleed_pct", 0f);
            this.sangraCada = d.getFloat("spell_bleed_every", 0f);
            this.magiaDot = d.getString("spell_dot", "bleed");
            this.magiaLento = d.getFloat("spell_slow", 0f);
            this.magiaSlot = d.getString("spell_slot", "orange");
            this.magiaExpSkill = d.getString("spell_exp_skill", null);
            this.magiaExp = d.getInt("spell_exp", 0);
            this.treino = d.getBoolean("training", false);
            this.economiaFlecha = d.getInt("ammo_save", 0);
            this.velocidadeAtaque = d.getFloat("atk_speed", 1f);
            this.efeitoAtaque = d.getString("atk_hit", null);
            this.projetilAtaque = d.getString("atk_proj", null);
            this.conjunto = d.getString("set", null);
            this.conjuntoXp = d.getInt("set_xp", 0);
            this.cap = d.getFloat("cap", 0f);
            this.regenHp = d.getInt("regen_hp", 0);
            this.regenMp = d.getInt("regen_mp", 0);
            this.regenSeg = d.getInt("regen_seg", 0);
            this.preco = d.getInt("price", 0);
            this.usavel = d.getBoolean("usable", false);
            this.empilhavel = d.getBoolean("stackable", false) || d.getBoolean("ammo", false);
            this.nome = d.getString("name", "");
            this.tipo = d.getString("type", "");
            this.slot = d.getString("slot", null);
            this.reqLevel = d.getInt("req_level", 0);
            this.reqClass = d.getString("req_class", "All");
            this.bonusDamage = d.getInt("bonus_damage", 0);
            this.defense = d.getInt("defense", 0);
            this.armour = d.getInt("armour", 0);
            this.stamina = d.getInt("stamina", 0);
            this.mana = d.getInt("mana", 0);
            this.fourthStatType = d.getString("fourth_stat_type", "");
            this.fourthStatValue = d.getInt("fourth_stat_value", 0);
            this.manaCost = d.getInt("mana_cost", 0);
            this.fullness = d.getInt("fullness", 0);
            this.healHp = d.getInt("heal_hp", 0);
            this.healMp = d.getInt("heal_mp", 0);
            this.icone = d.getString("icon", null);
            this.precoVendaFixo = d.getInt("sell_price", 0);
        }

        boolean ehPocao() { return healHp > 0 || healMp > 0; }
    }

    private final Map<String, EquipStats> ITEM_STATS = new LinkedHashMap<>();

    /** Recebe o item_db do servidor (sync_local_player) - nomes, tipos e stats dos itens. */
    public void carregarItemDb(JsonValue db) {
        if (db == null || !db.isObject()) return;
        ITEM_STATS.clear();
        for (JsonValue entrada = db.child; entrada != null; entrada = entrada.next) {
            if (entrada.isObject()) ITEM_STATS.put(entrada.name, new EquipStats(entrada));
        }
        atualizarGrade();
        atualizarGradeEquip();
        atualizarDetalhesEquip();
        atualizarDetalhes(itemSelecionado());
    }

    private com.badlogic.gdx.scenes.scene2d.ui.Cell<?> celulaJanela, celulaOrganizer;
    private float extraJanela = 0f;
    private static final float EXTRA_JANELA_MAX = 220f;

    /** Coluna de detalhes (Bag, trade, banco, loja) cresce ate' caber o texto
     * mais largo; a janela cresce junto quando e' a pagina aberta. */
    /** Quanto a janela ainda pode alargar sem sair da tela (no celular o
     * livro e' ampliado 1.2x a partir do centro). */
    private float limiteExtraTela() {
        if (root.getStage() == null || celulaOrganizer == null || celulaOrganizer.getActor() == null) return EXTRA_JANELA_MAX;
        float escala = Math.max(0.01f, celulaOrganizer.getActor().getScaleX());
        float cabe = (root.getStage().getWidth() - 8f) / escala - (COLUNA_LARGURA * 2f + JANELA_LARGURA);
        return Math.max(0f, Math.min(EXTRA_JANELA_MAX, cabe));
    }

    private static final float FOLGA_GAVETA_MAGIA = 16f;

    private void ajustarLarguraDetalhes() {
        if (celulaJanela == null) return;
        float limite = limiteExtraTela();
        float extraVisivel = 0f;
        for (Table d : new Table[]{bagDetalhes, tradeDetalhes, bancoDetalhes, lojaDetalhes, equipDetalhesEsquerda}) {
            boolean equip = d == equipDetalhesEsquerda;
            float base = equip ? COLUNA_EQUIP : COLUNA_BAG;
            // Equip: os detalhes ficam dentro de um scroll (a celula da coluna e' a dele).
            com.badlogic.gdx.scenes.scene2d.Actor caixa = equip && equipScrollEsquerda != null ? equipScrollEsquerda : d;
            // Equip: os detalhes saem da coluna quando nao tem item - ela volta ao normal.
            if (equip && equipColunaEsquerda != null && caixa.getParent() != equipColunaEsquerda) {
                com.badlogic.gdx.scenes.scene2d.ui.Cell<?> c = equipPage.getCell(equipColunaEsquerda);
                if (c != null && Math.abs(c.getMinWidth() - base) > 0.5f) { c.width(base); equipPage.invalidateHierarchy(); }
                continue;
            }
            if (!(caixa.getParent() instanceof Table) || !(caixa.getParent().getParent() instanceof Table)) continue;
            Table coluna = (Table) caixa.getParent();
            Table pagina = (Table) coluna.getParent();
            // Loja de magia (aba Learn): sem coluna da esquerda - a gaveta tem tudo e o botao.
            if (d == lojaDetalhes) {
                boolean esconder = modoLoja && lojaDeMagia() && !abaLojaVenda;
                coluna.setVisible(!esconder);
                com.badlogic.gdx.scenes.scene2d.ui.Cell<?> cc = pagina.getCell(coluna);
                if (esconder) {
                    if (cc != null && cc.getMinWidth() > 0.5f) { cc.width(0f); pagina.invalidateHierarchy(); }
                    continue;
                }
            }
            com.badlogic.gdx.scenes.scene2d.ui.Cell<?> cDet = coluna.getCell(caixa), cCol = pagina.getCell(coluna);
            if (cDet == null || cCol == null) continue;
            float pad = cDet.getPadLeft() + cDet.getPadRight();
            float precisa = d.getPrefWidth() + pad + 2f;
            // Loja: o botao de baixo ("Are you sure?" e' comprido) tambem tem que caber.
            if (d == lojaDetalhes && lojaAcao != null) precisa = Math.max(precisa, lojaAcao.getPrefWidth() + 14f);
            // Trade: o botao Send Offer tambem (a coluna alarga pro texto dele).
            if (d == tradeDetalhes && botaoEnviarTrade != null) precisa = Math.max(precisa, botaoEnviarTrade.getPrefWidth() + 14f);
            float extra = Math.min(limite, Math.max(0f, precisa - base));
            if (d == tradeDetalhes && botaoEnviarTrade != null) {
                com.badlogic.gdx.scenes.scene2d.ui.Cell<?> cBotao = coluna.getCell(botaoEnviarTrade);
                if (cBotao != null && Math.abs(cBotao.getMinWidth() - (base - 14f + extra)) > 0.5f) {
                    cBotao.width(base - 14f + extra);
                    coluna.invalidateHierarchy();
                }
            }
            if (d == lojaDetalhes && lojaRodapeAcao != null) {
                com.badlogic.gdx.scenes.scene2d.ui.Cell<?> cBotao = lojaRodapeAcao.getCell(lojaAcao);
                com.badlogic.gdx.scenes.scene2d.ui.Cell<?> cRodape = coluna.getCell(lojaRodapeAcao);
                if (cBotao != null && Math.abs(cBotao.getMinWidth() - (base - 14f + extra)) > 0.5f) {
                    cBotao.width(base - 14f + extra);
                    if (cRodape != null) cRodape.width(base - 14f + extra);
                    lojaRodapeAcao.invalidateHierarchy();
                }
            }
            if (Math.abs(cCol.getMinWidth() - (base + extra)) > 0.5f) {
                cCol.width(base + extra);
                cDet.width(base - pad + extra);
                coluna.invalidateHierarchy();
            }
            if (pagina.getParent() == mainWindow) extraVisivel = Math.max(extraVisivel, extra);
        }
        // Mercado: lista de itens + painel das ofertas lado a lado.
        if (modoMercado && mercadoPage.getParent() == mainWindow) extraVisivel = Math.max(extraVisivel, limite);
        // Aba Spells (lista de magias): a janela alarga ate' a descricao caber numa linha.
        if (conteudoMagias != null && spellsPage.getParent() == mainWindow && conteudoMagias.getStage() != null) {
            float esquerda = TAM_SLOT_ATALHO * 3f + 6f * 3f + 22f + 6f;
            // pads do painel + barra de rolagem + folga (sem ela a borda da direita
            // da gaveta ficava uns px pra fora da rolagem e era cortada).
            float disponivel = JANELA_LARGURA - 16f - esquerda - 20f - 14f - FOLGA_GAVETA_MAGIA;
            extraVisivel = Math.max(extraVisivel,
                Math.min(limite, Math.max(0f, conteudoMagias.getPrefWidth() - disponivel)));
        }
        if (Math.abs(extraVisivel - extraJanela) > 0.5f) {
            extraJanela = extraVisivel;
            celulaJanela.width(JANELA_LARGURA + extraJanela);
            celulaOrganizer.width(COLUNA_LARGURA * 2f + JANELA_LARGURA + extraJanela);
            root.invalidateHierarchy();
        }
    }

    private static String regiaoSlotVazio(String slot) {
        switch (slot) {
            case "Helm": return "ui/slots/HelmSlot";
            case "Necklace": return "ui/slots/NeckSlot";
            case "MainHand": return "ui/slots/SwordSlot";
            case "Hand": return "ui/slots/ShieldSlot";
            case "Chest": return "ui/slots/ChestSlot";
            case "Gloves": return "ui/slots/GlovesSlot";
            case "Boots": return "ui/slots/BootsSlot";
            case "Ring": return "ui/slots/RingSlot";
            default: return null;
        }
    }

    /** Slot onde o item entra: o "slot" do item_db do servidor; sem ele, deduzido
     *  do caminho (mesma regra de servidor.py::slot_do_item). null = nao equipavel. */
    private String slotDoItem(String caminho) {
        if (caminho == null) return null;
        EquipStats dados = ITEM_STATS.get(caminho);
        if (dados != null && dados.slot != null && !dados.slot.isEmpty()) return dados.slot;
        if (caminho.contains("/Weapons/") || caminho.endsWith("/Sword.tres")) return "MainHand";
        if (caminho.contains("/SecondHand/")) return "Hand";
        String nome = caminho.substring(caminho.lastIndexOf('/') + 1).toLowerCase();
        if (nome.contains("helm") || nome.contains("hat")) return "Helm";
        if (nome.contains("neck") || nome.contains("amulet")) return "Necklace";
        if (nome.contains("chest") || nome.contains("armor") || nome.contains("robe")) return "Chest";
        if (nome.contains("glove")) return "Gloves";
        if (nome.contains("boot")) return "Boots";
        if (nome.contains("ring")) return "Ring";
        return null;
    }

    private void construirPaginaEquip(Skin skin) {
        skin.add("equip-slot", UiSkin.retangulo(
            new Color(0.15f, 0.15f, 0.15f, 1f), new Color(0.21f, 0.21f, 0.21f, 1f), 2),
            com.badlogic.gdx.scenes.scene2d.utils.Drawable.class);
        skin.add("equip-slot-hover", UiSkin.retangulo(
            new Color(0.18f, 0.18f, 0.18f, 1f), new Color(0.32f, 0.32f, 0.32f, 1f), 2),
            com.badlogic.gdx.scenes.scene2d.utils.Drawable.class);
        skin.add("equip-slot-selected", UiSkin.retangulo(
            new Color(0.2f, 0.2f, 0.2f, 1f), new Color(0.95f, 0.65f, 0.24f, 1f), 2),
            com.badlogic.gdx.scenes.scene2d.utils.Drawable.class);

        // Coluna esquerda: grade de slots equipados + detalhes do slot selecionado.
        Table slots = new Table();
        slots.top().left();
        equipSlotsTabela = slots;
        for (String[] linha : SLOTS_EQUIP) {
            for (String nomeSlot : linha) {
                if (nomeSlot == null) {
                    slots.add().size(SLOT_EQUIP).pad(1.5f);
                    continue;
                }
                Button botao = novoBotaoSlotEquip();
                botao.addListener(new ChangeListener() {
                    @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                        selecionarSlotEquip(nomeSlot);
                    }
                });
                equipSlotButtons.put(nomeSlot, botao);
                slots.add(botao).size(SLOT_EQUIP).pad(1.5f);
            }
            slots.row();
        }

        equipColunaEsquerda = new Table();
        equipColunaEsquerda.top().left();
        equipColunaEsquerda.setBackground(UiSkin.retangulo(
            new Color(0.08f, 0.08f, 0.08f, 1f), new Color(0.35f, 0.35f, 0.35f, 1f), 1));
        equipDetalhesEsquerda.top().left();
        equipScrollEsquerda = scrollDetalhesEquip(equipDetalhesEsquerda);
        equipScrollDireita = scrollDetalhesEquip(equipDetalhesDireita);

        // Coluna direita: itens da bag que podem ser equipados + botao + detalhes.
        equipItemGrid.top().left();
        ScrollPane scroll = new ScrollPane(equipItemGrid, skin);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        scroll.setOverscroll(false, false);
        scroll.setFlickScroll(true);
        equipPainelGrade = new Table();
        equipPainelGrade.top().left();
        equipPainelGrade.add(scroll).grow().pad(12, 12, 6, 12);
        equipDetalhesDireita.top().left();

        TextButton.TextButtonStyle estiloEquipar = new TextButton.TextButtonStyle(
            skin.get("verde", TextButton.TextButtonStyle.class));
        estiloEquipar.font = skin.getFont("botao-texto-font");
        TextButton.TextButtonStyle estiloDesequipar = new TextButton.TextButtonStyle(
            skin.get("default", TextButton.TextButtonStyle.class));
        estiloDesequipar.font = skin.getFont("botao-texto-font");
        this.estiloEquipar = estiloEquipar;
        // Item de outra classe (ou level baixo): botao cinza e travado.
        estiloEquiparBloqueado = new TextButton.TextButtonStyle(
            skin.get("default", TextButton.TextButtonStyle.class));
        estiloEquiparBloqueado.font = skin.getFont("botao-texto-font");
        estiloEquiparBloqueado.fontColor = new Color(0.55f, 0.55f, 0.55f, 1f);
        estiloEquiparBloqueado.disabledFontColor = estiloEquiparBloqueado.fontColor;
        equipButton = new TextButton("Equip", estiloEquipar);
        equipButton.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                equiparSelecionado();
            }
        });
        unequipButton = new TextButton("Unequip", estiloDesequipar);
        unequipButton.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                desequiparSlotSelecionado();
            }
        });

        equipColunaDireita = new Table();
        equipColunaDireita.top().left();
        equipColunaDireita.setBackground(UiSkin.retangulo(
            new Color(0.165f, 0.165f, 0.165f, 1f), new Color(0.165f, 0.165f, 0.165f, 1f), 1));

        equipPage.add(equipColunaEsquerda).width(COLUNA_EQUIP).growY();
        equipPage.add(equipColunaDireita).grow();
        atualizarGradeEquip();
        atualizarDetalhesEquip();
    }

    /** Botao de slot da aba Equip. setChecked() feito pelo codigo NAO dispara
     *  ChangeListener (senao o listener chama a atualizacao de novo, em loop). */
    private Button novoBotaoSlotEquip() {
        Button novo = new Button(estiloSlotEquip());
        novo.setProgrammaticChangeEvents(false);
        return novo;
    }

    private Button.ButtonStyle estiloSlotEquip() {
        Button.ButtonStyle estilo = new Button.ButtonStyle();
        estilo.up = skin.getDrawable("equip-slot");
        estilo.over = skin.getDrawable("equip-slot-hover");
        estilo.down = skin.getDrawable("equip-slot-selected");
        estilo.checked = skin.getDrawable("equip-slot-selected");
        estilo.checkedOver = estilo.checked;
        return estilo;
    }

    private void selecionarSlotEquip(String slot) {
        selectedSlot = slot.equals(selectedSlot) ? "" : slot;
        selectedEquipCandidate = -1;
        atualizarGradeEquip();
        atualizarDetalhesEquip();
        if (modoTrade) {
            // Item que sumiu da bag sai da selecao.
            tradeSelecao.keySet().removeIf(id -> itemPorId(id) == null);
            if (itemPorId(tradeFoco) == null) tradeFoco = null;
            if (!tradeEnviado) {
                atualizarGradeTrade();
                atualizarDetalhesTrade();
            }
        }
    }

    private void atualizarGradeEquip() {
        equipCandidatos.clear();
        for (int i = 0; i < inventoryItems.size(); i++) {
            String caminho = inventoryItems.get(i).itemPath;
            String slotItem = slotDoItem(caminho);
            if (slotItem == null) continue; // comida etc. nao vai pra lista de equipar
            // Filtro: com um slot selecionado, so' mostra o que entra nele.
            if (!selectedSlot.isEmpty() && !selectedSlot.equals(slotItem)) continue;
            equipCandidatos.add(i);
        }
        equipItemGrid.clearChildren();
        if (equipCandidatos.isEmpty()) {
            Button vazio = novoBotaoSlotEquip();
            vazio.setDisabled(true);
            equipItemGrid.add(vazio).size(SLOT_EQUIP).pad(1.5f);
            return;
        }
        for (int n = 0; n < equipCandidatos.size(); n++) {
            final int indice = equipCandidatos.get(n);
            InventoryItem item = inventoryItems.get(indice);
            Button slotBtn = novoBotaoSlotEquip();
            slotBtn.setChecked(indice == selectedEquipCandidate);
            TextureAtlas.AtlasRegion textura = iconeDoItem(item.itemPath);
            Stack conteudoEquip = new Stack();
            if (textura != null) {
                Image icone = new Image(new TextureRegionDrawable(textura));
                icone.setScaling(Scaling.fit);
                Table moldura = new Table();
                moldura.add(icone).grow().pad(4);
                conteudoEquip.add(moldura);
            }
            // Pilha (flecha): quantidade no canto, igual a Bag.
            if (item.quantity > 1) {
                Table marcadores = new Table();
                marcadores.bottom();
                adicionarQuantidade(marcadores, item.quantity);
                conteudoEquip.add(marcadores);
            }
            slotBtn.add(conteudoEquip).grow().pad(2);
            slotBtn.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                    selectedEquipCandidate = (selectedEquipCandidate == indice) ? -1 : indice;
                    atualizarGradeEquip();
                    atualizarDetalhesEquip();
                }
            });
            equipItemGrid.add(slotBtn).size(SLOT_EQUIP).pad(1.5f);
            if ((n + 1) % COLUNAS_EQUIP == 0) equipItemGrid.row();
        }
    }

    private void atualizarDetalhesEquip() {
        for (Map.Entry<String, Button> entrada : equipSlotButtons.entrySet()) {
            entrada.getValue().setChecked(entrada.getKey().equals(selectedSlot));
        }
        String equipadoPath = selectedSlot.isEmpty() ? null : equippedItemPaths.get(selectedSlot);
        String candidatoPath = selectedEquipCandidate >= 0 && selectedEquipCandidate < inventoryItems.size()
            ? inventoryItems.get(selectedEquipCandidate).itemPath : null;
        if (candidatoPath == null || !equipCandidatos.contains(selectedEquipCandidate)) {
            candidatoPath = null;
            selectedEquipCandidate = -1;
        }

        // Esquerda: detalhes do item equipado no slot selecionado.
        equipColunaEsquerda.clearChildren();
        equipColunaEsquerda.add(equipSlotsTabela).top().left().pad(4).row();
        if (equipadoPath != null && candidatoPath == null) {
            preencherBlocoStats(equipDetalhesEsquerda, equipadoPath, null,
                equippedQuantidades.getOrDefault(slotDoItem(equipadoPath), 1));
            equipColunaEsquerda.add(separadorEquip()).growX().height(1).padTop(4).row();
            equipColunaEsquerda.add(equipScrollEsquerda).grow().top().left().pad(10, 12, 10, 12).row();
        }

        // Direita: grade; com candidato -> botao Equip + detalhes comparando
        // com o que esta equipado no slot dele; com slot equipado -> Unequip.
        equipColunaDireita.clearChildren();
        if (candidatoPath != null) {
            String slotAlvo = slotDoItem(candidatoPath);
            String atual = slotAlvo == null ? null : equippedItemPaths.get(slotAlvo);
            equipColunaDireita.add(equipPainelGrade).growX().height(ALTURA_GRADE_EQUIP - 54f).row();
            equipColunaDireita.add(equipButton).width(120).height(42).right().padRight(14).padBottom(8).row();
            equipColunaDireita.add(separadorEquip()).growX().height(1).row();
            preencherBlocoStats(equipDetalhesDireita, candidatoPath, atual == null ? "" : atual);
            equipColunaDireita.add(equipScrollDireita).grow().top().left().pad(10, 12, 10, 12);
            boolean bloqueado = !podeEquipar(candidatoPath);
            equipButton.setStyle(bloqueado ? estiloEquiparBloqueado : estiloEquipar);
            equipButton.setDisabled(bloqueado || (slotAlvo == null && selectedSlot.isEmpty()));
        } else {
            equipColunaDireita.add(equipPainelGrade).grow().row();
            if (equipadoPath != null) {
                equipColunaDireita.add(unequipButton).width(120).height(42).right().padRight(14).padBottom(10);
            }
        }
    }

    private ScrollPane scrollDetalhesEquip(Table detalhes) {
        ScrollPane sp = new ScrollPane(detalhes, skin);
        sp.setFadeScrollBars(false);
        sp.setScrollingDisabled(true, false);
        sp.setOverscroll(false, false);
        sp.setFlickScroll(true);
        return sp;
    }

    private Image separadorEquip() {
        return criarPreenchimentoBarra(new Color(0.35f, 0.35f, 0.35f, 1f));
    }

    private static Color corClasse(String classe) {
        if (classe == null) return Color.LIGHT_GRAY;
        switch (classe) {
            case "Knight": return Color.WHITE;
            case "Ranger": return Color.valueOf("4caf50");
            case "Mage": return Color.valueOf("a474d4");
            case "Bard": return Color.valueOf("ffd54f");
            default: return Color.LIGHT_GRAY;
        }
    }

    /** Nome do ADC como aparece pra classe logada: "Class" (sets universais)
     * vira a skill da classe; Magic no Bard vira Musicality (usa os sets do Mage).
     * null = item sem ADC. */
    private String quartoStatExibido(String tipo) {
        if (tipo == null || tipo.isEmpty() || "None".equals(tipo)) return null;
        if ("Class".equals(tipo)) {
            switch (classeJogador) {
                case "Mage": return "Magic";
                case "Ranger": return "Focus";
                case "Bard": return "Musicality";
                default: return "Melee";
            }
        }
        if ("Magic".equals(tipo) && "Bard".equals(classeJogador)) return "Musicality";
        return tipo;
    }

    /** Arma da mao principal: efeito/projetil do ataque basico (null = o da classe). */
    public String efeitoArmaEquipada() {
        EquipStats d = armaEquipada();
        return d == null ? null : d.efeitoAtaque;
    }

    public String projetilArmaEquipada() {
        EquipStats d = armaEquipada();
        if (d != null && d.projetilAtaque != null) return d.projetilAtaque;
        // Flecha com projetil proprio (Flint Arrow) na segunda mao.
        String flecha = equippedItemPaths.get("Hand");
        EquipStats f = flecha == null ? null : ITEM_STATS.get(flecha);
        return f == null ? null : f.projetilAtaque;
    }

    /** "atk_speed" da arma equipada (1 = normal). */
    public float velocidadeArmaEquipada() {
        EquipStats d = armaEquipada();
        return d == null ? 1f : d.velocidadeAtaque;
    }

    private EquipStats armaEquipada() {
        String caminho = equippedItemPaths.get("MainHand");
        return caminho == null ? null : ITEM_STATS.get(caminho);
    }

    private static Color corQuartoStat(String tipo) {
        if (tipo == null) return Color.WHITE;
        switch (tipo) {
            case "Magic": return Color.valueOf("a474d4");
            case "Focus": return Color.valueOf("00d084");
            case "Musicality": return Color.valueOf("f0a35a");
            case "Melee": return Color.valueOf("cccccc");
            default: return Color.WHITE;
        }
    }

    private static final Color COR_REQUISITO = Color.valueOf("ffff33");
    private static final Color COR_STAMINA = Color.valueOf("22e022");
    private static final Color COR_MANA = Color.valueOf("4a8cff");

    /**
     * Nome + requisitos + stats do item. compararCom != null mostra a diferenca
     * pro item equipado no mesmo slot ("" = slot vazio), ex: "Mana 10 (+10)".
     */
    private void preencherBlocoStats(Table bloco, String itemPath, String compararCom) {
        preencherBlocoStats(bloco, itemPath, compararCom, 1);
    }

    /** quantidade: tamanho da pilha - a linha de cap mostra o total dela. */
    private int quantidadeBloco = 1;

    private void preencherBlocoStats(Table bloco, String itemPath, String compararCom, int quantidade) {
        quantidadeBloco = Math.max(1, quantidade);
        bloco.clearChildren();
        bloco.top().left();
        // Fonte fina ("hud", sem contorno) reduzida; linhas na altura natural
        // da fonte (sem sobrepor) e sem espaco extra entre elas.
        // Mesma fonte das notificacoes da HUD (nitida): ver UiSkin.fonteDescricao.
        Label nome = new LabelPixel(nomeExibicao(itemPath), new Label.LabelStyle(UiSkin.fonteDescricao(skin, 15), Color.WHITE));
        nome.setWrap(true);
        // Largura do nome inteiro (ate' um limite): a coluna cresce pra ele
        // caber numa linha; maior que isso quebra a linha.
        float larguraNome = Math.min(COLUNA_BAG - 16f + limiteExtraTela(),
            new com.badlogic.gdx.graphics.g2d.GlyphLayout(nome.getStyle().font, nome.getText()).width + 2f);
        boolean colunaCresce = bloco == bagDetalhes || bloco == tradeDetalhes || bloco == bancoDetalhes || bloco == lojaDetalhes
            || bloco == equipDetalhesEsquerda;
        bloco.add(nome).growX().minWidth(colunaCresce ? larguraNome : 0f).left().padBottom(1).row();
        EquipStats dados = ITEM_STATS.get(itemPath);
        if (dados == null) return;
        EquipStats base = compararCom == null ? null : ITEM_STATS.get(compararCom);
        boolean comparar = compararCom != null;
        // Material (drop de mob): so' o nome, "Material" e o cap.
        if ("Material".equals(dados.tipo)) {
            bloco.add(linhaStat("Material", new Color(0.6f, 0.6f, 0.6f, 1f))).left().row();
            adicionarLinhaCap(bloco, dados);
            return;
        }
        if (dados.fullness > 0) {
            // Comida: coxinha + Fullness; HP/MP com o regen extra (buff).
            bloco.add(linhaComIcone(ICONE_FULLNESS, "Fullness " + dados.fullness, COR_FOME)).left().row();
            // Regen extra: soma no regen natural enquanto o buff durar.
            if (dados.regenHp > 0) bloco.add(linhaComIcone("ui/HPIcon", "+" + dados.regenHp + " Regen", COR_POCAO_HP)).left().row();
            if (dados.regenMp > 0) bloco.add(linhaComIcone("ui/MPIcon", "+" + dados.regenMp + " Regen", COR_POCAO_MP)).left().row();
            if (dados.regenHp > 0 || dados.regenMp > 0)
                bloco.add(linhaStat("for " + dados.regenSeg + " seconds", new Color(0.6f, 0.6f, 0.6f, 1f))).left().row();
            adicionarLinhaCap(bloco, dados);
            return;
        }
        if (dados.magia != null && !dados.magia.isEmpty()) {
            // Magia: requisito, custo, cooldown e o dano.
            bloco.add(linhaStat("Req. Lv " + dados.reqLevel, COR_REQUISITO)).left().row();
            bloco.add(linhaStat("Spell", Color.LIGHT_GRAY)).left().row();
            bloco.add(linhaStat(dados.magiaMana + " Mana", COR_MANA)).left().row();
            bloco.add(linhaStat(dados.magiaCd + "s Cooldown", Color.LIGHT_GRAY)).left().row();
            bloco.add(linhaStat(danoTotalMagia(dados) + "% Damage", COR_SET)).left().padTop(6).row();
            if (dados.sangraTicks > 0) bloco.add(linhaStat(textoSangramento(dados), corDoDot(dados))).left().row();
            if (dados.magiaLento > 0) bloco.add(linhaStat("Slows for " + porcento(dados.magiaLento) + "s", Color.LIGHT_GRAY)).left().row();
            if (dados.magiaGolpes > 1) bloco.add(linhaStat(textoGolpes(dados), Color.LIGHT_GRAY)).left().row();
            bloco.add(linhaStat(dados.magiaArea ? "Area Damage" : "Single Target", COR_ALVO_MAGIA)).left().row();
            return;
        }
        if (dados.usavel) {
            // Scroll: o tipo e o que faz.
            bloco.add(linhaStat(dados.tipo.isEmpty() ? "Scroll" : dados.tipo, Color.LIGHT_GRAY)).left().row();
            adicionarLinhaCap(bloco, dados);
            Label efeito = linhaStat("Teleports you to the city. Can't be used in battle.", new Color(0.7f, 0.85f, 1f, 1f));
            efeito.setWrap(true);
            bloco.add(efeito).growX().left().row();
            return;
        }
        if (dados.ehPocao()) {
            // Pocao: "Potion" e quanto cura (valor maximo; o servidor varia um pouco).
            bloco.add(linhaStat("Potion", Color.LIGHT_GRAY)).left().row();
            // "+(icone de HP/MP da HUD) 50".
            if (dados.healHp > 0) bloco.add(linhaCura("ui/HPIcon", dados.healHp, COR_POCAO_HP)).left().row();
            if (dados.healMp > 0) bloco.add(linhaCura("ui/MPIcon", dados.healMp, COR_POCAO_MP)).left().row();
            adicionarLinhaCap(bloco, dados);
            return;
        }
        bloco.add(linhaStat("Req. Lv " + dados.reqLevel, COR_REQUISITO)).left().row();
        // Vermelho = classe errada (o servidor pune quem equipa item de outra classe).
        boolean classeCerta = "All".equals(dados.reqClass) || classeJogador.equals(dados.reqClass);
        bloco.add(linhaStat(("All".equals(dados.reqClass) ? "Universal" : dados.reqClass) + " " + dados.tipo,
            classeCerta ? corClasse(dados.reqClass) : Color.valueOf("ff4a4a"))).left().row();
        adicionarLinhaStat(bloco, "Attack", dados.bonusDamage, base == null ? 0 : base.bonusDamage, comparar, Color.WHITE);
        adicionarLinhaStat(bloco, "Defense", dados.defense, base == null ? 0 : base.defense, comparar, Color.WHITE);
        adicionarLinhaStat(bloco, "Armour", dados.armour, base == null ? 0 : base.armour, comparar, Color.WHITE);
        adicionarLinhaStat(bloco, "Stamina", dados.stamina, base == null ? 0 : base.stamina, comparar, COR_STAMINA);
        adicionarLinhaStat(bloco, "Mana", dados.mana, base == null ? 0 : base.mana, comparar, COR_MANA);
        String quarto = quartoStatExibido(dados.fourthStatType);
        if (quarto != null) {
            int anterior = base != null && quarto.equals(quartoStatExibido(base.fourthStatType)) ? base.fourthStatValue : 0;
            adicionarLinhaStat(bloco, quarto, dados.fourthStatValue, anterior, comparar, corQuartoStat(quarto));
        }
        // Arma rapida (adaga): espadinha + quanto mais rapido bate.
        if (dados.velocidadeAtaque != 1f) {
            String mult = Math.abs(dados.velocidadeAtaque - Math.round(dados.velocidadeAtaque)) < 0.01f
                ? String.valueOf(Math.round(dados.velocidadeAtaque))
                : String.format(java.util.Locale.US, "%.1f", dados.velocidadeAtaque);
            bloco.add(linhaComIcone("ui/items/StarterSword", mult + "x Atk Speed", Color.WHITE)).left().row();
        }
        // Mana gasta por ataque (varinha). Sem "(+n)": nao e' bonus, e' custo.
        if (dados.manaCost > 0) bloco.add(linhaStat(dados.manaCost + " Mana per hit", COR_MANA)).left().row();
        // Cap: sempre o ultimo stat (antes das descricoes em azul).
        adicionarLinhaCap(bloco, dados);
        if (dados.treino) {
            // Arma de treino: o que ela faz, em topicos (azul, igual o scroll).
            List<String> topicos = new ArrayList<>(java.util.Arrays.asList(
                "Hits 1% of target HP (max 100)",
                "+5% skill gain",
                "No HP/MP regen",
                "No spells",
                "Locks the creature for 5s"));
            if (dados.economiaFlecha > 0) topicos.add(dados.economiaFlecha + "% chance to save arrows");
            bloco.add(linhaStat("Training Weapon", COR_SET)).left().padTop(6).row();
            for (String t : topicos) bloco.add(linhaStat(t, COR_SET)).left().padLeft(10).row();
        } else if (dados.economiaFlecha > 0) {
            bloco.add(linhaStat(dados.economiaFlecha + "% chance to save arrows", COR_SET)).left().padTop(6).row();
        }
        if (dados.conjunto != null && !dados.conjunto.isEmpty()) adicionarBlocoSet(bloco, dados);
    }

    /** "(bolsinha) 15": quanto de cap 1 unidade do item ocupa. */
    private void adicionarLinhaCap(Table bloco, EquipStats dados) {
        adicionarLinhaValorNpc(bloco, dados);
        if (dados == null || dados.cap <= 0f) return;
        float total = dados.cap * quantidadeBloco; // pilha: cap total
        String valor = Math.abs(total - Math.round(total)) < 0.001f ? String.valueOf(Math.round(total))
            : String.format(java.util.Locale.US, "%.1f", total);
        bloco.add(linhaComIcone("ui/currency/BagIcon", valor, Color.LIGHT_GRAY)).left().row();
    }

    /** Quanto o NPC paga (pilha: o total), em moedas: "1 [ouro] 25 [prata] 15 [cobre]". */
    private void adicionarLinhaValorNpc(Table bloco, EquipStats dados) {
        if (dados == null) return;
        long unidade = dados.precoVendaFixo > 0 ? dados.precoVendaFixo : dados.preco > 0 ? Math.max(1, dados.preco / 2) : 0;
        if (unidade <= 0) return;
        long total = unidade * quantidadeBloco;
        Table linha = new Table();
        for (int i = MOEDAS_TRADE.length - 1; i >= 0; i--) {
            long q = (total / VALOR_MOEDA[i]) % (i == MOEDAS_TRADE.length - 1 ? Long.MAX_VALUE : 100L);
            if (q <= 0) continue;
            linha.add(linhaStat(String.valueOf(q), corMoeda(MOEDAS_TRADE[i]))).padRight(2);
            TextureAtlas.AtlasRegion r = atlas.findRegion("ui/currency/" + MOEDAS_TRADE[i]);
            if (r != null) {
                Image img = new Image(new TextureRegionDrawable(r));
                img.setScaling(Scaling.fit);
                linha.add(img).size(16f).padRight(6);
            }
        }
        bloco.add(linha).left().padTop(2).row();
    }

    private static final Color COR_SET = new Color(0.7f, 0.85f, 1f, 1f); // azul do scroll
    private static final Color COR_SET_OFF = new Color(0.5f, 0.5f, 0.5f, 1f);

    /** "Outlander Set (2/4)", as pecas (azul = equipada, cinza = falta) e o
     * bonus (azul so' com o set completo). */
    private void adicionarBlocoSet(Table bloco, EquipStats dados) {
        List<String> pecas = new ArrayList<>();
        for (Map.Entry<String, EquipStats> e : ITEM_STATS.entrySet()) {
            if (dados.conjunto.equals(e.getValue().conjunto)) pecas.add(e.getKey());
        }
        int equipadas = 0;
        for (String peca : pecas) if (equippedItemPaths.containsValue(peca)) equipadas++;
        boolean completo = equipadas >= pecas.size();
        bloco.add(linhaStat(dados.conjunto + " Set (" + equipadas + "/" + pecas.size() + ")", COR_SET)).left().padTop(6).row();
        for (String peca : pecas) {
            bloco.add(linhaStat(nomeExibicao(peca), equippedItemPaths.containsValue(peca) ? COR_SET : COR_SET_OFF)).left().row();
        }
        if (dados.conjuntoXp > 0) {
            Color cor = completo ? COR_SET : COR_SET_OFF;
            Table linha = new Table();
            linha.add(linhaStat("Bonus: " + dados.conjuntoXp + "%", cor)).padRight(4);
            TextureAtlas.AtlasRegion regiao = atlas.findRegion("ui/XPIcon");
            if (regiao != null) {
                Image img = new Image(new TextureRegionDrawable(regiao));
                img.setScaling(Scaling.fit);
                linha.add(img).size(18f).padRight(4);
            }
            linha.add(linhaStat("Extra XP", cor));
            bloco.add(linha).left().padTop(4).row();
        }
    }

    private void adicionarLinhaStat(Table bloco, String nome, int valor, int anterior, boolean comparar, Color cor) {
        if (valor == 0 && (!comparar || anterior == 0)) return;
        String texto = valor + " " + nome;
        if (comparar) {
            int diferenca = valor - anterior;
            texto += " (" + (diferenca >= 0 ? "+" : "") + diferenca + ")";
        }
        bloco.add(linhaStat(texto, cor)).left().row();
    }

    /** Coxinha de frango (448,448 no atlas): icone de Fullness e da aba Buy. */
    private static final String ICONE_FULLNESS = "sheet/r18_c5";

    /** Icone pequeno do atlas + texto (detalhes de comida). */
    private Table linhaComIcone(String icone, String texto, Color cor) {
        Table linha = new Table();
        TextureAtlas.AtlasRegion regiao = atlas.findRegion(icone);
        if (regiao != null) {
            Image img = new Image(new TextureRegionDrawable(regiao));
            img.setScaling(Scaling.fit);
            linha.add(img).size(18f).padRight(4);
        }
        linha.add(linhaStat(texto, cor));
        return linha;
    }

    private Table linhaCura(String icone, int valor, Color cor) {
        Table linha = new Table();
        linha.add(linhaStat("+", cor)).padRight(3);
        TextureAtlas.AtlasRegion regiao = atlas.findRegion(icone);
        if (regiao != null) {
            Image img = new Image(new TextureRegionDrawable(regiao));
            img.setScaling(Scaling.fit);
            linha.add(img).size(18f).padRight(4);
        }
        linha.add(linhaStat(String.valueOf(valor), cor));
        return linha;
    }

    /** Texto "hud" menor: troca pela fonte gerada nesse tamanho (nitida);
     *  outros estilos continuam com setFontScale. */
    private void escalarFonte(Label label, float fator) {
        Label.LabelStyle estilo = label.getStyle();
        if (estilo != null && estilo.font == skin.getFont("hud-font") || estilo != null && estilo.font.getData() != null
                && fontesHud.contains(estilo.font)) {
            Label.LabelStyle novo = new Label.LabelStyle(UiSkin.fonteHudEm(skin, fator), estilo.fontColor);
            novo.background = estilo.background;
            label.setStyle(novo);
            fontesHud.add(novo.font);
            label.setFontScale(1f);
        } else {
            label.setFontScale(fator);
        }
    }

    private final java.util.Set<com.badlogic.gdx.graphics.g2d.BitmapFont> fontesHud = new java.util.HashSet<>();

    /** Moedas / barra de cap / rodapes: mesma fonte nitida das descricoes. */
    private static final int TAM_TEXTO_RODAPE = Math.round(15 * FONTE_STATS);
    /** Texto dentro da barra de cap (Bag/loja/banco): menor que o das moedas. */
    private static final int TAM_TEXTO_CAP = Math.round(12 * FONTE_STATS);

    private Label.LabelStyle estiloNitido(int tamanho) {
        return new Label.LabelStyle(UiSkin.fonteDescricao(skin, tamanho), Color.WHITE);
    }

    private Label linhaStat(String texto, Color cor) {
        // LabelPixel: origem presa no pixel de tela (senao a letra caia entre 2 pixels e embacava).
        Label label = new LabelPixel(texto, new Label.LabelStyle(UiSkin.fonteDescricao(skin, 13), Color.WHITE));
        label.setColor(cor);
        return label;
    }

    /** Classe certa e level suficiente (o servidor recusa igual). */
    private boolean podeEquipar(String caminho) {
        EquipStats dados = caminho == null ? null : ITEM_STATS.get(caminho);
        if (dados == null) return true;
        return ("All".equals(dados.reqClass) || classeJogador.equals(dados.reqClass)) && dados.reqLevel <= nivelAtual;
    }

    private void equiparSelecionado() {
        if (selectedEquipCandidate < 0 || selectedEquipCandidate >= inventoryItems.size()
            || !socket.isConnected()) return;
        InventoryItem item = inventoryItems.get(selectedEquipCandidate);
        if (!podeEquipar(item.itemPath)) return;
        String slotItem = slotDoItem(item.itemPath);
        String slot = slotItem != null ? slotItem : selectedSlot;
        if (item.instanceId.isEmpty() || slot.isEmpty()) return;
        String instanceId = item.instanceId;
        String payload = GameSocket.obj(w -> { w.set("instance_id", instanceId); w.set("slot", slot); });
        socket.emitRaw("equip_item", payload);
        selectedEquipCandidate = -1;
        selectedSlot = slot;
    }

    private void desequiparSlotSelecionado() {
        if (selectedSlot.isEmpty() || !socket.isConnected()) return;
        String slot = selectedSlot;
        String payload = GameSocket.obj(w -> w.set("slot", slot));
        socket.emitRaw("unequip_item", payload);
    }

    /** Numero de quantidade colado no canto inferior esquerdo do slot (Bag e
     * Equip). O Label reserva espaco pro "pe" das letras (descent) embaixo -
     * numero nao tem pe, entao sobrava um vao; o padBottom negativo desconta
     * isso e o numero encosta na borda sem sair do slot. */
    /** Numero de quantidade padrao (fonte com contorno, sem escala, nitido). */
    public Label rotuloQuantidade(int quantidade) {
        Label rotulo = new LabelPixel(String.valueOf(quantidade), skin.get("quantidade", Label.LabelStyle.class));
        rotulo.setAlignment(Align.bottomRight);
        return rotulo;
    }

    private void adicionarQuantidade(Table marcadores, int quantidade) {
        // Canto inferior DIREITO, colado (sem margem) - igual em toda GUI.
        Label rotulo = rotuloQuantidade(quantidade);
        marcadores.add().expandX();
        marcadores.add(rotulo).right().bottom().padBottom(rotulo.getStyle().font.getDescent());
    }

    /** Mana gasta por ataque basico pela arma da mao principal (0 = nao gasta). */
    public int custoManaArmaEquipada() {
        String caminho = equippedItemPaths.get("MainHand");
        EquipStats dados = caminho == null ? null : ITEM_STATS.get(caminho);
        return dados == null ? 0 : dados.manaCost;
    }

    /** Quantidade de um item equipado mudou (ex: flecha gasta, sync_vitals ammo_qty). */
    public void definirQuantidadeEquipada(String slot, int quantidade) {
        if (!equippedItemPaths.containsKey(slot)) return;
        equippedQuantidades.put(slot, quantidade);
        redesenharSlotsEquipados();
    }

    /** Icone de cada slot equipado; item empilhavel (flechas) mostra a
     * quantidade no canto inferior esquerdo, igual na Bag. */
    private void redesenharSlotsEquipados() {
        for (Map.Entry<String, Button> entrada : equipSlotButtons.entrySet()) {
            Button botao = entrada.getValue();
            botao.clearChildren();
            String caminho = equippedItemPaths.get(entrada.getKey());
            TextureAtlas.AtlasRegion textura = caminho == null ? null : iconeDoItem(caminho);
            if (textura == null) textura = atlas.findRegion(regiaoSlotVazio(entrada.getKey()));
            if (textura == null) continue;
            Image icone = new Image(new TextureRegionDrawable(textura));
            icone.setScaling(Scaling.fit);
            int qtd = equippedQuantidades.getOrDefault(entrada.getKey(), 1);
            if (caminho == null || qtd <= 1) {
                botao.add(icone).grow().pad(caminho == null ? 4 : 6);
                continue;
            }
            Table moldura = new Table();
            moldura.add(icone).grow().pad(4); // +2 do pad da pilha = 6, igual os outros slots
            Table marcadores = new Table();
            marcadores.bottom();
            adicionarQuantidade(marcadores, qtd);
            botao.add(new Stack(moldura, marcadores)).grow().pad(2);
        }
    }

    public void atualizarEquipados(JsonValue dados) {
        equippedItemPaths.clear();
        equippedQuantidades.clear();
        if (dados != null && dados.isObject()) {
            for (JsonValue entrada = dados.child; entrada != null; entrada = entrada.next) {
                String caminho = entrada.getString("item", "");
                if (!caminho.isEmpty()) {
                    equippedItemPaths.put(entrada.name, caminho);
                    equippedQuantidades.put(entrada.name, Math.max(1, entrada.getInt("qty", 1)));
                }
            }
        }
        redesenharSlotsEquipados();
        atualizarDetalhesEquip();
        // A comparacao (+x) dos detalhes da Bag depende do que esta equipado.
        if (favoriteButton != null) atualizarDetalhes(itemSelecionado());
    }

    // ===================== SKILLS =====================

    // Progresso (0-1) ate' o proximo nivel - anel do retrato (WorldScreen).
    private float fracaoPrincipal = 0f, fracaoDefesa = 0f;
    public float fracaoSkillPrincipal() { return fracaoPrincipal; }
    public float fracaoDefesa() { return fracaoDefesa; }
    /** Nome da skill da classe (Magic/Musicality/Focus/Melee). */
    public String nomeSkillPrincipal() { return skillPrincipal()[1]; }
    public Color corDaSkillPrincipal() { return corSkillPrincipal(skillPrincipal()[0]); }
    public static final Color COR_SKILL_DEFESA = Color.valueOf("6ab7ff");

    private String[] skillPrincipal() {
        switch (classeJogador) {
            case "Ranger": return new String[]{"distance", "Focus"}; // chave do servidor continua "distance"
            case "Mage": return new String[]{"magic", "Magic"};
            case "Bard": return new String[]{"musicality", "Musicality"};
            default: return new String[]{"melee", "Melee"};
        }
    }

    /** {icone, nome} da skill que a magia treina (chave do servidor). */
    private String[] skillDaMagia(String chave) {
        switch (chave) {
            case "distance": return new String[]{"ui/FocusIcon", "Focus"};
            case "magic": return new String[]{"ui/items/StarterStaff", "Magic"};
            case "musicality": return new String[]{"ui/MusicalityIcon", "Musicality"};
            default: return new String[]{atlas.findRegion("ui/MeeleIcon") != null ? "ui/MeeleIcon" : "ui/MeleeIcon", "Melee"};
        }
    }

    private String iconeSkillPrincipal() {
        switch (classeJogador) {
            case "Ranger": return "ui/FocusIcon";
            case "Mage": return "ui/items/StarterStaff";
            case "Bard": return "ui/MusicalityIcon";
            // No .atlas a regiao esta grafada "MeeleIcon".
            default: return atlas.findRegion("ui/MeeleIcon") != null ? "ui/MeeleIcon" : "ui/MeleeIcon";
        }
    }

    private static final class BarraSkill {
        final Label nivelLabel;
        final Label percentLabel;
        final Image preenchimento;
        final Table alinhador;
        final boolean casasDecimais;

        BarraSkill(Label nivelLabel, Label percentLabel, Image preenchimento, Table alinhador, boolean casasDecimais) {
            this.nivelLabel = nivelLabel;
            this.percentLabel = percentLabel;
            this.preenchimento = preenchimento;
            this.alinhador = alinhador;
            this.casasDecimais = casasDecimais;
        }
    }

    /** Uma linha do topo: [nome][nivel] .... [icone][barra com %]. */
    private BarraSkill criarBarraSkill(Table pai, String iconRegion, String nome, Color corTexto,
                                       Color corBarra, Color corPercentual, boolean comNivel) {
        Label nomeLabel = new LabelPixel(nome, skin.get("hud", Label.LabelStyle.class));
        nomeLabel.setColor(corTexto);
        Label nivelLabel = null;
        pai.add(nomeLabel).left().width(LARGURA_NOME_SKILL);
        if (comNivel) {
            nivelLabel = new LabelPixel("1", skin.get("hud", Label.LabelStyle.class));
            nivelLabel.setColor(corTexto);
            pai.add(nivelLabel).left().expandX();
        } else {
            pai.add().expandX();
        }

        TextureAtlas.AtlasRegion region = iconRegion == null ? null : atlas.findRegion(iconRegion);
        if (region != null) {
            Image icone = new Image(new TextureRegionDrawable(region));
            icone.setScaling(Scaling.fit);
            pai.add(icone).size(TAMANHO_ICONE_SKILL).padRight(8);
        } else {
            pai.add().size(TAMANHO_ICONE_SKILL).padRight(8);
        }

        Stack pilha = new Stack();
        Table fundo = new Table();
        fundo.setBackground(UiSkin.retangulo(new Color(0.055f, 0.055f, 0.055f, 1f),
            new Color(0.2f, 0.2f, 0.2f, 1f), 1));
        pilha.add(fundo);
        Table alinhador = new Table();
        alinhador.left().pad(1);
        Image preenchimento = criarPreenchimentoBarra(corBarra);
        alinhador.add(preenchimento).size(0f, ALTURA_BARRA_SKILL - 2f);
        pilha.add(alinhador);
        Label percentLabel = new LabelPixel("0%", skin.get("hud", Label.LabelStyle.class));
        percentLabel.setColor(corPercentual);
        percentLabel.setAlignment(Align.center);
        pilha.add(percentLabel);
        pai.add(pilha).width(LARGURA_BARRA_SKILL).height(ALTURA_BARRA_SKILL).row();

        return new BarraSkill(nivelLabel, percentLabel, preenchimento, alinhador, comNivel);
    }

    private static final Color COR_NIVEL = Color.valueOf("ffeb3b");
    private static final Color COR_BARRA_XP = Color.valueOf("f5b82e");
    private static final Color COR_DEFESA = Color.valueOf("6ab7ff");
    private static final Color COR_FOME = Color.valueOf("f0a35a");
    private static final Color COR_POCAO_HP = Color.valueOf("ff6b6b");
    private static final Color COR_POCAO_MP = Color.valueOf("8f6bff");
    /** Mesmo valor de servidor.py::FULLNESS_MAX. */
    public static final float FULLNESS_MAX = 50f;
    private static final Color COR_BARRA_FOME = Color.valueOf("8a4b2a");

    private static Color corSkillPrincipal(String chaveServidor) {
        if (chaveServidor == null) return Color.WHITE;
        switch (chaveServidor) {
            case "magic": return Color.valueOf("a474d4");
            case "distance": return Color.valueOf("00d084");
            case "musicality": return Color.valueOf("ffa24e");
            case "melee": return Color.valueOf("cccccc");
            default: return Color.WHITE;
        }
    }

    private void construirPaginaSkills(Skin skin) {
        skillsPage.top().left();
        String[] principal = skillPrincipal();
        Color corPrincipal = corSkillPrincipal(principal[0]);

        Table barras = new Table();
        barras.top().left();
        // Espaco entre linhas em TODAS as celulas - se ficar so' na barra,
        // icone/nome centralizam numa linha mais alta e descem em relacao a ela.
        barras.defaults().padBottom(10);
        barraNivel = criarBarraSkill(barras, "ui/XPIcon", "Level", COR_NIVEL, COR_BARRA_XP, Color.WHITE, true);
        barraPrincipal = criarBarraSkill(barras, iconeSkillPrincipal(), principal[1], corPrincipal, corPrincipal, Color.WHITE, true);
        barraDefesa = criarBarraSkill(barras, "ui/DefenseIcon", "Defense", COR_DEFESA, COR_DEFESA, Color.WHITE, true);
        barraFome = criarBarraSkill(barras, "ui/HungerIcon", "Fullness", COR_FOME, COR_BARRA_FOME, COR_FOME, false);

        Image separador = criarPreenchimentoBarra(new Color(0.42f, 0.42f, 0.42f, 1f));

        Table statusEsquerda = new Table();
        statusEsquerda.top().left();
        statusEsquerda.add(linhaStatus("ui/ClassIcon", "Class", "class")).left().row();
        statusEsquerda.add(linhaStatus("ui/XPIcon", "Experience", "exp")).left().row();
        statusEsquerda.add(linhaStatus("ui/NextLevelIcon", "Next Level", "next")).left().row();
        statusEsquerda.add(linhaStatus("ui/CritIcon", "Crit Chance", "crit")).left().row();
        statusEsquerda.add(linhaStatus(atlas.findRegion("ui/DodgeIcon") != null ? "ui/DodgeIcon" : "ui/BlockIcon", "Dodge Chance", "block")).left().row();
        statusEsquerda.add(linhaStatus("ui/currency/BagIcon", "Capacity", "capacity")).left().row();

        Table statusDireita = new Table();
        statusDireita.top().left();
        // Regen a cada 5s: base (level) + extra dos equipamentos.
        Table linhaRegenHp = linhaStatus("ui/HPIcon", "HP Regen", "regen_hp");
        Table linhaRegenMp = linhaStatus("ui/MPIcon", "Mana Regen", "regen_mp");
        pintarLinhaStatus(linhaRegenHp, COR_POCAO_HP);
        pintarLinhaStatus(linhaRegenMp, COR_POCAO_MP);
        statusDireita.add(linhaRegenHp).left().row();
        statusDireita.add(linhaRegenMp).left().row();
        statusDireita.add(linhaStatus("ui/TotalXP", "Total EXP", "total_exp")).left().row();
        statusDireita.add(linhaStatus("ui/TotalKills", "Total Kills", "total_kills")).left().row();

        Table status = new Table();
        status.top().left();
        status.add(statusEsquerda).top().left().expandX().fillX();
        status.add(statusDireita).top().left().expandX().fillX();

        skillsPage.add(barras).growX().height(ALTURA_TOPO_SKILLS).top().left().padTop(4).row();
        skillsPage.add(separador).growX().height(1).padBottom(12).row();
        skillsPage.add(status).growX().top().left().padLeft(4).row();
        skillsPage.add().grow();
        atualizarSkills(null, 1, 0, 0);
    }

    private Image criarPreenchimentoBarra(Color cor) {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        Image imagem = new Image(new TextureRegionDrawable(new com.badlogic.gdx.graphics.g2d.TextureRegion(
            new Texture(pixmap))));
        pixmap.dispose();
        imagem.setColor(cor);
        return imagem;
    }

    private void atualizarBarra(BarraSkill barra, int nivel, float percentual) {
        atualizarBarra(barra, nivel, percentual, 0f);
    }

    /** bonusItens > 0: "35+12" (o que os equipamentos somam nessa skill). */
    private void atualizarBarra(BarraSkill barra, int nivel, float percentual, float bonusItens) {
        float pct = Math.max(0f, Math.min(1f, percentual));
        if (barra.nivelLabel != null) {
            String extra = "";
            if (bonusItens > 0.001f) {
                extra = "+" + (Math.abs(bonusItens - Math.round(bonusItens)) < 0.01f
                    ? String.valueOf(Math.round(bonusItens)) : String.format(java.util.Locale.US, "%.1f", bonusItens));
            }
            barra.nivelLabel.setText(nivel + extra);
        }
        barra.percentLabel.setText(barra.casasDecimais
            ? String.format(java.util.Locale.US, "%.2f%%", pct * 100f)
            : String.format(java.util.Locale.US, "%.0f%%", pct * 100f));
        barra.alinhador.getCell(barra.preenchimento).width((LARGURA_BARRA_SKILL - 2f) * pct);
        barra.alinhador.invalidateHierarchy();
    }

    private static int getHitsToLevel(int level, float multiplicador) {
        double L = level - 3;
        return (int) Math.ceil((L * Math.pow(1.0825, L) + (1.0825 * L) + 30.0) * multiplicador);
    }

    private static float getSkillMultiplier(String classe, boolean defesa) {
        if (defesa) {
            if ("Knight".equals(classe)) return 0.8f;
            if ("Ranger".equals(classe)) return 1.0f;
            if ("Mage".equals(classe) || "Bard".equals(classe)) return 1.2f;
        } else {
            if ("Knight".equals(classe)) return 1.2f;
            if ("Ranger".equals(classe)) return 1.0f;
            if ("Mage".equals(classe) || "Bard".equals(classe)) return 0.8f;
        }
        return 1.0f;
    }

    private static int getExpForLevel(int level) {
        if (level <= 1) return 0;
        return (int) ((50.0 / 3.0) * (Math.pow(level, 3) - 6 * Math.pow(level, 2) + 17 * level - 12));
    }

    private static String formatarNumero(long valor) {
        return String.format(java.util.Locale.US, "%,d", valor).replace(',', '.');
    }

    private static String formatarPercentual(float valor) {
        return (valor == Math.floor(valor)
            ? String.valueOf((int) valor)
            : String.format(java.util.Locale.US, "%.1f", valor)) + "%";
    }

    /**
     * Campos opcionais lidos de "skills" (o servidor ainda nao manda todos):
     * fullness (0-FULLNESS_MAX), crit_chance, block_chance e total_exp.
     */
    public void atualizarSkills(JsonValue skills, int level, int exp, int kills) {
        this.ultimasSkills = skills;
        // Magias aprendidas (servidor.py: skills["spells_known"]).
        JsonValue conhecidas = skills != null ? skills.get("spells_known") : null;
        java.util.Set<String> novas = new java.util.LinkedHashSet<>();
        if (conhecidas != null && conhecidas.isArray())
            for (JsonValue v = conhecidas.child; v != null; v = v.next) if (v.isString()) novas.add(v.asString());
        if (!novas.equals(magiasConhecidas)) {
            magiasConhecidas.clear();
            magiasConhecidas.addAll(novas);
            aoMudarBagOuAtalhos();
            if (lojaFoco != null && magiasConhecidas.contains(lojaFoco)) lojaFoco = null;
            if (modoLoja) atualizarTelaLoja();
        }
        this.nivelAtual = level;
        this.expAtual = exp;
        this.killsAtual = kills;

        int expAtualNivel = getExpForLevel(level);
        int expProximo = getExpForLevel(level + 1);
        float pctNivel = expProximo > expAtualNivel ? (float) (exp - expAtualNivel) / (expProximo - expAtualNivel) : 0f;
        atualizarBarra(barraNivel, level, pctNivel);

        String[] principal = skillPrincipal();
        int nivelPrincipal = skills != null ? skills.getInt(principal[0], 10) : 10;
        int hitsPrincipal = skills != null ? skills.getInt(principal[0] + "_hits", 0) : 0;
        int necessarioPrincipal = getHitsToLevel(nivelPrincipal + 1, getSkillMultiplier(classeJogador, false));
        float pctPrincipal = necessarioPrincipal > 0 ? (float) hitsPrincipal / necessarioPrincipal : 0f;
        atualizarBarra(barraPrincipal, nivelPrincipal, pctPrincipal, skills != null ? skills.getFloat("adc_itens", 0f) : 0f);
        fracaoPrincipal = Math.max(0f, Math.min(1f, pctPrincipal));

        int nivelDefesa = skills != null ? skills.getInt("defense", 10) : 10;
        int hitsDefesa = skills != null ? skills.getInt("defense_hits", 0) : 0;
        int necessarioDefesa = getHitsToLevel(nivelDefesa + 1, getSkillMultiplier(classeJogador, true));
        float pctDefesa = necessarioDefesa > 0 ? (float) hitsDefesa / necessarioDefesa : 0f;
        atualizarBarra(barraDefesa, nivelDefesa, pctDefesa, skills != null ? skills.getFloat("def_itens", 0f) : 0f);
        fracaoDefesa = Math.max(0f, Math.min(1f, pctDefesa));

        float fullness = skills != null ? skills.getFloat("fullness", FULLNESS_MAX) : FULLNESS_MAX;
        atualizarBarra(barraFome, 0, fullness / FULLNESS_MAX);
        barraFome.percentLabel.setText(Math.round(fullness) + "/" + Math.round(FULLNESS_MAX));

        float crit = skills != null ? skills.getFloat("crit_chance", 10f) : 10f;
        float block = skills != null ? skills.getFloat("block_chance", 10f) : 10f;
        long totalExp = skills != null ? skills.getLong("total_exp", exp) : exp;

        valoresStatus.get("class").setText(classeJogador);
        // XP so' do level atual (o acumulado de todos fica no Total Exp):
        // "Experience" = quanto ja' fez nesse level, "Next Level" = quanto o level pede.
        valoresStatus.get("exp").setText(formatarNumero(Math.max(0, exp - expAtualNivel)));
        valoresStatus.get("next").setText(formatarNumero(Math.max(0, expProximo - expAtualNivel)));
        valoresStatus.get("crit").setText(formatarPercentual(crit));
        valoresStatus.get("block").setText(formatarPercentual(block));
        atualizarTextoCapacidade();
        valoresStatus.get("regen_hp").setText(textoRegen(skills, "regen_hp"));
        valoresStatus.get("regen_mp").setText(textoRegen(skills, "regen_mp"));
        valoresStatus.get("total_exp").setText(formatarNumero(totalExp));
        valoresStatus.get("total_kills").setText(formatarNumero(kills));
    }

    private void atualizarTextoCapacidade() {
        Label capacidade = valoresStatus.get("capacity");
        if (capacidade != null) {
            capacidade.setText(String.format(java.util.Locale.US, "%.0f/%.0f", capacityUsed, capacityMaximum));
        }
    }

    /** [icone] Rotulo    valor - o valor fica alinhado numa coluna fixa. */
    /** Rotulo e valor de uma linha de status na cor dada (ex: regen em vermelho/roxo). */
    private static void pintarLinhaStatus(Table linha, Color cor) {
        for (com.badlogic.gdx.scenes.scene2d.Actor a : linha.getChildren()) {
            if (a instanceof Label) a.setColor(cor);
        }
    }

    private static String textoRegen(JsonValue skills, String chave) {
        JsonValue v = skills == null ? null : skills.get(chave);
        if (v == null || !v.isArray() || v.size < 2) return "-";
        return v.getInt(0) + "+" + v.getInt(1);
    }

    private Table linhaStatus(String iconRegion, String rotulo, String chave) {
        Table linha = new Table();
        TextureAtlas.AtlasRegion region = iconRegion == null ? null : atlas.findRegion(iconRegion);
        if (region != null) {
            Image icone = new Image(new TextureRegionDrawable(region));
            icone.setScaling(Scaling.fit);
            linha.add(icone).size(TAMANHO_ICONE_STATUS).padRight(8);
        } else {
            linha.add().size(TAMANHO_ICONE_STATUS).padRight(8);
        }
        Label r = new LabelPixel(rotulo, skin.get("hud", Label.LabelStyle.class));
        Label v = new LabelPixel("", skin.get("hud", Label.LabelStyle.class));
        linha.add(r).left().width(LARGURA_ROTULO_STATUS);
        linha.add(v).left();
        linha.padBottom(8);
        valoresStatus.put(chave, v);
        return linha;
    }

    // ===================== VANITY (skins) =====================
    // Abas na ordem da print (roupa, cabeca, acessorio, pele). Catalogo vem do
    // servidor (skin_db no sync_local_player, ja filtrado pela classe); o que
    // vale de verdade e' confirmado pelo servidor em skins_synced.
    private static final String[] CATEGORIAS_VANITY = {"base", "helm", "body", "acc"};
    private static final float COLUNA_VANITY = 200f;
    private static final float ESCALA_PREVIEW = 3.5f;
    private final Map<String, List<String[]>> skinDb = new LinkedHashMap<>(); // cat -> {caminho, nome}
    private final Map<String, String[]> skinsEquipadas = new LinkedHashMap<>(); // cat -> {caminho, cor}
    private final Map<String, String[]> skinsRascunho = new LinkedHashMap<>();
    private String categoriaVanity = "base";
    private final Table vanityAbas = new Table();
    private final Table vanityOpcoes = new Table();
    private final Table vanityCores = new Table();
    private final Table vanityPreview = new Table();
    private TextButton botaoAplicarSkins;
    private TextButton.TextButtonStyle estiloAplicarNormal, estiloAplicarAlterado;
    // Variacoes de cada matiz (saturacao, brilho): pastel, clara, viva,
    // profunda, escura e apagada (cor "terrosa", ex: vermelho amarronzado).
    private static final float[][] VARIACOES_COR = {
        {0.30f, 1.00f}, {0.55f, 1.00f}, {0.85f, 0.95f}, {0.88f, 0.70f}, {0.80f, 0.45f}, {0.42f, 0.60f}};

    // Depois de VARIACOES_COR: static inicializa na ordem do arquivo.
    private static final List<Color> PALETA = criarPaleta();

    private static List<Color> criarPaleta() {
        List<Color> cores = new ArrayList<>();
        // 2 linhas de neutros: cinzas (sem preto puro, que some no contorno)
        // e bege -> marrom.
        for (String h : new String[]{"ffffff", "e0e0e0", "c0c0c0", "a0a0a0", "808080", "646464", "4a4a4a", "343434",
                                     "f3dcc0", "e0bf96", "c99a6b", "b07d4f", "8a5a35", "6b4026", "553220", "3e2416"}) {
            cores.add(Color.valueOf(h));
        }
        // 24 matizes (de 15 em 15 graus: da' ciano, ciano-esverdeado,
        // carmesim...) x 6 variacoes, em 3 blocos de 8 colunas: cada coluna
        // e' um matiz e as 6 linhas do bloco descem as variacoes dele.
        for (int bloco = 0; bloco < 3; bloco++) {
            for (float[] var : VARIACOES_COR) {
                for (int col = 0; col < 8; col++) {
                    Color c = new Color();
                    c.fromHsv((bloco * 8 + col) * 15f, var[0], var[1]);
                    c.a = 1f;
                    cores.add(c);
                }
            }
        }
        return cores;
    }

    private void construirPaginaVanity(Skin skin) {
        skin.add("vanity-selected", UiSkin.retangulo(
            new Color(0.15f, 0.15f, 0.15f, 1f), new Color(0.1f, 0.9f, 0.1f, 1f), 2),
            com.badlogic.gdx.scenes.scene2d.utils.Drawable.class);

        // Esquerda: abas de categoria, preview em 4 direcoes, Equip/Reset.
        vanityAbas.top().left();
        vanityPreview.setBackground(UiSkin.retangulo(
            new Color(0.165f, 0.165f, 0.165f, 1f), new Color(0.22f, 0.22f, 0.22f, 1f), 1));

        TextButton.TextButtonStyle estiloBotao = new TextButton.TextButtonStyle(
            skin.get("default", TextButton.TextButtonStyle.class));
        estiloBotao.font = skin.getFont("botao-texto-font");
        // Cinza sem alteracoes; verde quando o rascunho difere do que esta equipado.
        estiloAplicarNormal = estiloBotao;
        estiloAplicarAlterado = new TextButton.TextButtonStyle(skin.get("verde", TextButton.TextButtonStyle.class));
        estiloAplicarAlterado.font = skin.getFont("botao-texto-font");
        TextButton equipar = new TextButton("Apply", estiloBotao);
        botaoAplicarSkins = equipar;
        equipar.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                equiparSkins();
            }
        });
        TextButton resetar = new TextButton("Reset", estiloBotao);
        resetar.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                copiarSkins(skinsEquipadas, skinsRascunho);
                atualizarVanity();
            }
        });
        Table botoesVanity = new Table();
        botoesVanity.add(equipar).growX().height(36).padRight(6);
        botoesVanity.add(resetar).growX().height(36);

        Table colunaEsquerda = new Table();
        colunaEsquerda.top().left();
        colunaEsquerda.setBackground(UiSkin.retangulo(
            new Color(0.08f, 0.08f, 0.08f, 1f), new Color(0.35f, 0.35f, 0.35f, 1f), 1));
        colunaEsquerda.add(vanityAbas).growX().pad(6, 8, 0, 8).row();
        colunaEsquerda.add().grow().row();
        colunaEsquerda.add(vanityPreview).growX().height(150).pad(0, 8, 6, 8).row();
        colunaEsquerda.add(botoesVanity).growX().pad(0, 8, 8, 8);

        // Direita: opcoes da categoria (em cima) e paleta de cores (embaixo).
        vanityOpcoes.top().left();
        ScrollPane scrollOpcoes = new ScrollPane(vanityOpcoes, skin);
        scrollOpcoes.setFadeScrollBars(false);
        scrollOpcoes.setScrollingDisabled(true, false);
        scrollOpcoes.setOverscroll(false, false);
        vanityCores.top().left();
        ScrollPane scrollCores = new ScrollPane(vanityCores, skin);
        scrollCores.setFadeScrollBars(false);
        scrollCores.setScrollingDisabled(true, false);
        scrollCores.setOverscroll(false, false);

        Table colunaDireita = new Table();
        colunaDireita.top().left();
        colunaDireita.setBackground(UiSkin.retangulo(
            new Color(0.165f, 0.165f, 0.165f, 1f), new Color(0.165f, 0.165f, 0.165f, 1f), 1));
        colunaDireita.add(scrollOpcoes).growX().height(176).pad(10, 10, 6, 10).row();
        colunaDireita.add(separadorEquip()).growX().height(1).row();
        colunaDireita.add(scrollCores).grow().pad(6, 10, 8, 10);

        vanityPage.add(colunaEsquerda).width(COLUNA_VANITY).growY();
        vanityPage.add(colunaDireita).grow();
        atualizarVanity();
    }

    /** Catalogo (skin_db) + skins atuais, ambos vindos do sync_local_player. */
    public void carregarSkins(JsonValue db, JsonValue skins) {
        if (db != null && db.isObject()) {
            skinDb.clear();
            for (JsonValue cat = db.child; cat != null; cat = cat.next) {
                List<String[]> opcoes = new ArrayList<>();
                for (JsonValue op = cat.child; op != null; op = op.next) {
                    opcoes.add(new String[]{op.getString("caminho", ""), op.getString("nome", "")});
                }
                skinDb.put(cat.name, opcoes);
            }
        }
        atualizarSkinsEquipadas(skins);
    }

    /** Skins confirmadas pelo servidor (login ou skins_synced). */
    public void atualizarSkinsEquipadas(JsonValue skins) {
        skinsEquipadas.clear();
        for (String cat : SkinsUtil.ORDEM_CAMADAS) {
            String caminho = SkinsUtil.caminho(skins, cat);
            if (caminho != null && !caminho.isEmpty()) {
                skinsEquipadas.put(cat, new String[]{caminho, SkinsUtil.corHex(skins, cat)});
            }
        }
        copiarSkins(skinsEquipadas, skinsRascunho);
        atualizarVanity();
    }

    /** true se o rascunho (o que esta no preview) difere do equipado. */
    private boolean skinsAlteradas() {
        if (skinsRascunho.size() != skinsEquipadas.size()) return true;
        for (Map.Entry<String, String[]> e : skinsRascunho.entrySet()) {
            String[] equipada = skinsEquipadas.get(e.getKey());
            if (equipada == null || !equipada[0].equals(e.getValue()[0])
                || !equipada[1].equalsIgnoreCase(e.getValue()[1])) return true;
        }
        return false;
    }

    private static void copiarSkins(Map<String, String[]> de, Map<String, String[]> para) {
        para.clear();
        for (Map.Entry<String, String[]> e : de.entrySet()) para.put(e.getKey(), e.getValue().clone());
    }

    private void atualizarVanity() {
        if (botaoAplicarSkins != null) {
            botaoAplicarSkins.setStyle(skinsAlteradas() ? estiloAplicarAlterado : estiloAplicarNormal);
        }
        montarAbasVanity();
        montarOpcoesVanity();
        montarCoresVanity();
        montarPreviewVanity();
    }

    private Button.ButtonStyle estiloVanity(boolean selecionado) {
        Button.ButtonStyle estilo = new Button.ButtonStyle();
        estilo.up = skin.getDrawable(selecionado ? "vanity-selected" : "equip-slot");
        estilo.over = skin.getDrawable(selecionado ? "vanity-selected" : "equip-slot-hover");
        estilo.down = skin.getDrawable("vanity-selected");
        return estilo;
    }

    /** Quadro "parado de frente" de uma skin, pra icone de aba/opcao. */
    private Image iconeSkin(String caminho, String corHex) {
        TextureAtlas.AtlasRegion tira = SkinsUtil.regiao(atlas, caminho);
        if (tira == null) return null;
        Image img = new Image(new TextureRegionDrawable(SkinsUtil.quadro(tira, SkinsUtil.FRAME_BAIXO)));
        img.setScaling(Scaling.fit);
        if (corHex != null) img.setColor(SkinsUtil.cor(corHex));
        return img;
    }

    private void montarAbasVanity() {
        vanityAbas.clearChildren();
        for (String cat : CATEGORIAS_VANITY) {
            Button aba = new Button(estiloVanity(cat.equals(categoriaVanity)));
            String[] escolhida = skinsRascunho.get(cat);
            List<String[]> opcoes = skinDb.get(cat);
            String caminho = escolhida != null ? escolhida[0]
                : opcoes != null && !opcoes.isEmpty() ? opcoes.get(0)[0]
                : "base".equals(cat) ? SkinsUtil.BASE_PADRAO : null;
            // Icone (quadro de frente da peca atual) + nome da categoria.
            Image icone = iconeSkin(caminho, escolhida != null ? escolhida[1] : null);
            if (icone != null) aba.add(icone).size(36).padLeft(6).padRight(8);
            else aba.add().size(36).padLeft(6).padRight(8);
            Label nome = new LabelPixel(nomeCategoriaVanity(cat), skin.get("hud", Label.LabelStyle.class));
            escalarFonte(nome, 0.8f);
            aba.add(nome).left().expandX();
            // Categoria sem nada pra essa classe fica apagada (igual a asa da print).
            if (opcoes == null || opcoes.isEmpty()) aba.setColor(1f, 1f, 1f, 0.35f);
            aba.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                    categoriaVanity = cat;
                    atualizarVanity();
                }
            });
            // Uma "gaveta" por linha, largura toda da coluna.
            vanityAbas.add(aba).growX().height(44).pad(2, 0, 2, 0).row();
        }
    }

    private static String nomeCategoriaVanity(String cat) {
        switch (cat) {
            case "base": return "Skin";
            case "body": return "Clothe";
            case "helm": return "Hair/Hat";
            case "acc": return "Back";
            default: return cat;
        }
    }

    private void montarOpcoesVanity() {
        vanityOpcoes.clearChildren();
        List<String[]> opcoes = new ArrayList<>();
        // So' cabelo/chapeu e costas podem ficar vazios; pele e roupa sao obrigatorias.
        if ("helm".equals(categoriaVanity) || "acc".equals(categoriaVanity)) opcoes.add(null);
        List<String[]> doDb = skinDb.get(categoriaVanity);
        if (doDb != null) opcoes.addAll(doDb);
        String[] atual = skinsRascunho.get(categoriaVanity);
        int coluna = 0;
        for (String[] opcao : opcoes) {
            boolean selecionada = opcao == null ? atual == null : atual != null && atual[0].equals(opcao[0]);
            Button botao = new Button(estiloVanity(selecionada));
            if (opcao == null) {
                Image nada = new Image(new TextureRegionDrawable(atlas.findRegion("ui/Negate")));
                nada.setScaling(Scaling.fit);
                nada.setColor(1f, 1f, 1f, 0.4f);
                botao.add(nada).size(20);
            } else {
                // Todas as opcoes mostram a cor escolhida pra categoria.
                Image icone = iconeSkin(opcao[0], atual != null ? atual[1] : null);
                if (icone != null) botao.add(icone).grow().pad(5);
            }
            botao.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                    if (opcao == null) {
                        skinsRascunho.remove(categoriaVanity);
                    } else {
                        String[] anterior = skinsRascunho.get(categoriaVanity);
                        // Mantem a cor escolhida ao trocar de modelo.
                        String cor = "base".equals(categoriaVanity) || anterior == null ? "ffffffff" : anterior[1];
                        skinsRascunho.put(categoriaVanity, new String[]{opcao[0], cor});
                    }
                    atualizarVanity();
                }
            });
            vanityOpcoes.add(botao).size(SLOT_EQUIP).pad(1.5f);
            if (++coluna % 5 == 0) vanityOpcoes.row();
        }
    }

    private void montarCoresVanity() {
        vanityCores.clearChildren();
        // Skin nao tem paleta: o tom de pele e' escolhido pelo modelo
        // (Light/Dark/Soul), nao pintando a pele.
        if ("base".equals(categoriaVanity)) return;
        String[] atual = skinsRascunho.get(categoriaVanity);
        int coluna = 0;
        for (Color cor : PALETA) {
            String hex = SkinsUtil.hex(cor);
            boolean selecionada = atual != null && atual[1].equalsIgnoreCase(hex);
            Button.ButtonStyle estilo = new Button.ButtonStyle();
            estilo.up = UiSkin.retangulo(cor, selecionada ? Color.WHITE : new Color(0.06f, 0.06f, 0.06f, 1f), 2);
            estilo.over = UiSkin.retangulo(cor, new Color(0.75f, 0.75f, 0.75f, 1f), 2);
            Button celula = new Button(estilo);
            // Sem peca escolhida nessa categoria, nao ha o que pintar.
            if (atual == null) celula.setColor(1f, 1f, 1f, 0.35f);
            celula.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                    String[] escolhida = skinsRascunho.get(categoriaVanity);
                    if (escolhida == null) return;
                    escolhida[1] = hex;
                    atualizarVanity();
                }
            });
            vanityCores.add(celula).size(34).pad(1.5f);
            if (++coluna % 8 == 0) vanityCores.row();
        }
    }

    /** Personagem montado com as camadas do rascunho, nas 4 direcoes. */
    // Previews andando parados: os 2 quadros de passo de cada direcao, na
    // mesma ordem do mundo (WorldScreen.FRAME_ANDAR_*). Baixo, esquerda,
    // direita, cima.
    private static final int[][] ANDAR_PREVIEW = {{4, 5}, {7, 6}, {9, 8}, {1, 2}};
    private static final float TEMPO_QUADRO_PREVIEW = 0.45f;

    /** Camada do preview trocando de quadro sozinha. Todas comecam juntas
     * (tempo global), entao as camadas ficam sincronizadas. */
    private static final class QuadroAndando extends Image {
        private final TextureRegionDrawable[] quadros;
        private int atual = -1;
        QuadroAndando(com.badlogic.gdx.graphics.g2d.TextureRegion tira, int[] indices) {
            quadros = new TextureRegionDrawable[indices.length];
            for (int k = 0; k < indices.length; k++) quadros[k] = new TextureRegionDrawable(SkinsUtil.quadro(tira, indices[k]));
            trocar();
        }
        private void trocar() {
            long ms = com.badlogic.gdx.utils.TimeUtils.millis();
            int idx = (int) ((ms / (long) (TEMPO_QUADRO_PREVIEW * 1000f)) % quadros.length);
            if (idx != atual) {
                atual = idx;
                setDrawable(quadros[idx]);
            }
        }
        @Override public void act(float delta) {
            super.act(delta);
            trocar();
        }
    }

    private void montarPreviewVanity() {
        vanityPreview.clearChildren();
        for (int i = 0; i < ANDAR_PREVIEW.length; i++) {
            Stack pilha = new Stack();
            float altura = 17f;
            for (String cat : SkinsUtil.ORDEM_CAMADAS) {
                String[] escolhida = skinsRascunho.get(cat);
                String caminho = escolhida != null ? escolhida[0] : "base".equals(cat) ? SkinsUtil.BASE_PADRAO : null;
                TextureAtlas.AtlasRegion tira = SkinsUtil.regiao(atlas, caminho);
                if (tira == null) continue;
                Image camada = new QuadroAndando(tira, ANDAR_PREVIEW[i]);
                camada.setScaling(Scaling.stretch);
                if (escolhida != null) camada.setColor(SkinsUtil.cor(escolhida[1]));
                // Cada camada no tamanho dela (16 x altura da tira), ancorada
                // nos pes - igual o mundo desenha.
                com.badlogic.gdx.scenes.scene2d.ui.Container<Image> encaixe =
                    new com.badlogic.gdx.scenes.scene2d.ui.Container<>(camada);
                encaixe.size(SkinsUtil.FRAME_LARGURA * ESCALA_PREVIEW, tira.getRegionHeight() * ESCALA_PREVIEW).bottom();
                pilha.add(encaixe);
                altura = Math.max(altura, tira.getRegionHeight());
            }
            Table celula = new Table();
            celula.add(pilha).size(SkinsUtil.FRAME_LARGURA * ESCALA_PREVIEW, altura * ESCALA_PREVIEW);
            vanityPreview.add(celula).expand().pad(2);
            if (i == 1) vanityPreview.row();
        }
    }

    private void equiparSkins() {
        if (!socket.isConnected()) return;
        JsonValue raiz = new JsonValue(JsonValue.ValueType.object);
        JsonValue skins = new JsonValue(JsonValue.ValueType.object);
        for (Map.Entry<String, String[]> e : skinsRascunho.entrySet()) {
            JsonValue item = new JsonValue(JsonValue.ValueType.object);
            item.addChild("caminho", new JsonValue(e.getValue()[0]));
            item.addChild("cor", new JsonValue(e.getValue()[1]));
            skins.addChild(e.getKey(), item);
        }
        raiz.addChild("skins", skins);
        socket.emitRaw("update_skins", raiz.toJson(com.badlogic.gdx.utils.JsonWriter.OutputType.json));
    }

    private Color corMoeda(String tipo) {
        switch (tipo) {
            case "Copper": return Color.valueOf("ff8747");
            case "Silver": return Color.valueOf("d5d7dc");
            case "Gold": return Color.valueOf("ffd34e");
            case "Platinum": return Color.valueOf("d9e2ef");
            default: return Color.WHITE;
        }
    }

    private void atualizarGrade() {
        inventoryGrid.clearChildren();
        int quantidadeSlots = inventoryItems.size();
        for (int i = 0; i < quantidadeSlots; i++) {
            final int indice = i;
            Button.ButtonStyle estilo = new Button.ButtonStyle();
            estilo.up = skinDrawable("bag-slot");
            estilo.over = skinDrawable("bag-slot-hover");
            estilo.down = skinDrawable("bag-slot-selected");
            estilo.checked = skinDrawable("bag-slot-selected");
            estilo.checkedOver = estilo.checked;
            InventoryItem itemSlot = i < inventoryItems.size() ? inventoryItems.get(i) : null;
            if (modoExclusao && itemSlot != null && idsParaExcluir.contains(itemSlot.instanceId)) {
                estilo.up = estilo.over = estilo.down = skinDrawable("bag-slot-delete");
                estilo.checked = estilo.checkedOver = estilo.up;
            }
            Button slot = new Button(estilo);
            slot.setProgrammaticChangeEvents(false);
            slot.setChecked(!modoExclusao && i == selectedItem);
            // No modo de exclusao favoritos nao reagem ao clique (nem visualmente).
            if (modoExclusao && itemSlot != null && itemSlot.favorite) slot.setDisabled(true);
            if (i < inventoryItems.size()) {
                InventoryItem item = inventoryItems.get(i);
                Stack conteudo = new Stack();
                TextureAtlas.AtlasRegion textura = iconeDoItem(item.itemPath);
                if (textura != null) {
                    Image icone = new Image(new TextureRegionDrawable(textura));
                    icone.setScaling(Scaling.fit);
                    // Icone com margem propria; os marcadores (estrela/quantidade)
                    // ficam colados no canto, so' dentro da borda do slot.
                    Table moldura = new Table();
                    moldura.add(icone).grow().pad(3);
                    conteudo.add(moldura);
                }
                if (item.quantity > 1 || item.favorite) {
                    // Quantidade no canto inferior esquerdo, estrelinha de
                    // favorito (mesmo icone do botao) no inferior direito.
                    Table marcadores = new Table();
                    marcadores.bottom();
                    if (item.favorite) {
                        Image estrela = new Image(new TextureRegionDrawable(atlas.findRegion("ui/Star")));
                        estrela.setScaling(Scaling.fit);
                        marcadores.add(estrela).size(16f).left().bottom();
                    }
                    if (item.quantity > 1) adicionarQuantidade(marcadores, item.quantity);
                    else marcadores.add().expandX();
                    conteudo.add(marcadores);
                }
                if (temCooldown(item.itemPath)) conteudo.add(sobreposicaoCooldown(item.itemPath));
                slot.add(conteudo).grow().pad(2);
            }
            slot.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                    if (modoExclusao) {
                        alternarMarcaExclusao(indice);
                        return;
                    }
                    if (indice >= inventoryItems.size()) {
                        selectedItem = -1;
                        selectedInstanceId = "";
                        atualizarDetalhes(null);
                    } else {
                        selectedItem = indice;
                        selectedInstanceId = inventoryItems.get(indice).instanceId;
                        atualizarDetalhes(inventoryItems.get(indice));
                    }
                    atualizarGrade();
                }
            });
            inventoryGrid.add(slot).size(SLOT_EQUIP).pad(1.5f);
            if ((i + 1) % COLUNAS_INVENTARIO == 0) inventoryGrid.row();
        }
    }

    private com.badlogic.gdx.scenes.scene2d.utils.Drawable skinDrawable(String name) {
        return skin.getDrawable(name);
    }

    /** Icone sem "sangria": com a tela ampliada (escala quebrada) o filtro
     * Nearest pegava 1 pixel do sprite vizinho no atlas e aparecia uma linha
     * fina na borda do slot. Recua a UV 1/4 de texel em cada lado. */
    private final Map<TextureAtlas.AtlasRegion, TextureAtlas.AtlasRegion> iconesSemSangria = new java.util.HashMap<>();

    private TextureAtlas.AtlasRegion semSangria(TextureAtlas.AtlasRegion r) {
        if (r == null) return null;
        TextureAtlas.AtlasRegion pronto = iconesSemSangria.get(r);
        if (pronto != null) return pronto;
        pronto = new TextureAtlas.AtlasRegion(r);
        float du = 0.25f / r.getTexture().getWidth(), dv = 0.25f / r.getTexture().getHeight();
        pronto.setRegion(r.getU() + du, r.getV() + dv, r.getU2() - du, r.getV2() - dv);
        pronto.packedWidth = r.packedWidth;
        pronto.packedHeight = r.packedHeight;
        iconesSemSangria.put(r, pronto);
        return pronto;
    }

    public TextureAtlas.AtlasRegion iconeDoItem(String caminhoItem) {
        return semSangria(iconeDoItemBruto(caminhoItem));
    }

    private TextureAtlas.AtlasRegion iconeDoItemBruto(String caminhoItem) {
        // Item com "icon" no ITEM_DB (ex: pocoes) usa essa regiao do atlas.
        EquipStats comIcone = caminhoItem == null ? null : ITEM_STATS.get(caminhoItem);
        if (comIcone != null && comIcone.icone != null) {
            TextureAtlas.AtlasRegion r = atlas.findRegion(comIcone.icone);
            if (r != null) return r;
        }
        String nome = caminhoItem == null ? "" : caminhoItem.substring(caminhoItem.lastIndexOf('/') + 1);
        int extensao = nome.lastIndexOf('.');
        if (extensao >= 0) nome = nome.substring(0, extensao);
        String[] tentativas = {nome, nome.equals("StarterSheet") ? "StarterMusicsheet" : ""};
        for (String tentativa : tentativas) {
            if (tentativa.isEmpty()) continue;
            TextureAtlas.AtlasRegion region = atlas.findRegion("ui/items/" + tentativa);
            if (region != null) return region;
        }
        String lower = nome.toLowerCase();
        if (lower.contains("arrow")) return atlas.findRegion("ui/items/StarterArrow");
        if (lower.contains("sword")) return atlas.findRegion("ui/items/StarterSword");
        if (lower.contains("shield")) return atlas.findRegion("ui/items/StarterShield");
        if (lower.contains("bow")) return atlas.findRegion("ui/items/StarterBow");
        if (lower.contains("cookie")) return atlas.findRegion("sheet/r17_c1");
        if (lower.contains("staff")) return atlas.findRegion("ui/items/StarterStaff");
        if (lower.contains("flute")) return atlas.findRegion("ui/items/StarterFlute");
        if (lower.contains("book")) return atlas.findRegion("ui/items/StarterBook");
        return atlas.findRegion("ui/currency/BagIcon");
    }

    private void atualizarDetalhes(InventoryItem item) {
        if (modoExclusao) {
            // No modo de exclusao a coluna fica so' com o "N Items Selected".
            bagDetalhes.clearChildren();
            return;
        }
        if (item == null) {
            bagDetalhes.clearChildren();
            favoriteButton.setDisabled(true);
            trashButton.setDisabled(inventoryItems.isEmpty());
            return;
        }
        // Mesmos detalhes da aba Equip; equipamento compara com o que esta no slot dele.
        String slot = slotDoItem(item.itemPath);
        String equipado = slot == null ? null : equippedItemPaths.get(slot);
        preencherBlocoStats(bagDetalhes, item.itemPath,
            ITEM_STATS.containsKey(item.itemPath) ? (equipado == null ? "" : equipado) : null, item.quantity);
        EquipStats comida = ITEM_STATS.get(item.itemPath);
        // Comida/pocao nao mostram "Quantity" (a quantidade ja' aparece no icone).
        boolean ehPocao = comida != null && comida.ehPocao();
        boolean ehComida = comida != null && (comida.fullness > 0 || ehPocao || comida.usavel);
        if (item.quantity > 1 && !ehComida) bagDetalhes.add(linhaStat("Quantity " + item.quantity, Color.LIGHT_GRAY)).left().row();
        if (item.favorite) bagDetalhes.add(linhaStat("Favorite", new Color(1f, 0.82f, 0.24f, 1f))).left().row();
        if (ehComida && !item.instanceId.isEmpty()) {
            TextButton.TextButtonStyle estiloComer = new TextButton.TextButtonStyle(
                skin.get("verde", TextButton.TextButtonStyle.class));
            estiloComer.font = skin.getFont("botao-texto-font");
            // Pocao: "Use" (o servidor decide: eat_food usa comida ou pocao).
            TextButton comer = new TextButton(ehPocao || comida.usavel ? "Use" : "Eat", estiloComer);
            String id = item.instanceId;
            comer.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener() {
                @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                    if (socket.isConnected()) socket.emitRaw("eat_food", GameSocket.obj(w -> w.set("instance_id", id)));
                }
            });
            bagDetalhes.add(comer).left().size(90, 34).padTop(6).row();
        }
        favoriteButton.setDisabled(item.instanceId.isEmpty());
        trashButton.setDisabled(false);
    }

    // ===================== TRADE =====================
    // Duas telas no lugar do livro (abas laterais escondidas), mesmo visual
    // da Bag: (1) escolher os itens - clique marca/desmarca, item com mais de
    // 1 abre um campo pra digitar a quantia; Cancel / Send Trade (vira
    // "Waiting..." ate o outro mandar tambem). (2) com os dois prontos: em
    // cima a MINHA oferta, embaixo a do outro (cada um se ve como o player 1);
    // clicar num item mostra nome/stats na esquerda; Cancel / Accept.
    // Tudo validado no servidor (servidor.py, TRADE).

    private boolean modoTrade = false;
    private String tradeOutro = null;
    private String secaoAntesDoTrade = "Bag";
    private Table colunaEsquerdaAbas, colunaDireitaAbas;
    private final Table tradePage = new Table();
    private final Table tradeConfirmPage = new Table();
    private final Table tradeGrid = new Table();
    private final Table tradeDetalhes = new Table();
    private final Map<String, Integer> tradeSelecao = new LinkedHashMap<>(); // instanceId -> qty
    private String tradeFoco = null; // instanceId mostrado na esquerda
    private boolean tradeEnviado = false, tradeAceito = false;
    private TextButton botaoEnviarTrade, botaoAceitarTrade;
    private Label statusMeuAceite;
    private Label tituloTrade, statusTradeConfirm, tituloMinhaOferta, tituloOfertaOutro;
    private final Table gradeMinhaOferta = new Table(), gradeOfertaOutro = new Table();
    private final Table confirmDetalhes = new Table();
    private JsonValue minhaOferta, ofertaOutro;
    private com.badlogic.gdx.scenes.scene2d.ui.TextField campoQtdTrade;
    private Label passoTrade, resumoTrade, passoConfirm;
    private static final Color COR_PASSO = new Color(0.62f, 0.62f, 0.62f, 1f);

    private static final Color COR_TRADE_SELECIONADO = new Color(0f, 1f, 0f, 1f); // verde puro
    private static final boolean MOBILE_LIVRO = FONTE_STATS < 1f;
    // Moedas no trade: slot de cada uma (chave "$Nome" em tradeSelecao, valor
    // = quantas daquela moeda). Aparecem as que valem ate' a maior que o
    // player tem (com 20 silver aparece copper pra trocar 1 silver em 100).
    private static final String[] MOEDAS_TRADE = {"Copper", "Silver", "Gold", "Platinum"};
    private static final long[] VALOR_MOEDA = {1L, 100L, 10_000L, 1_000_000L};
    private static final String PREFIXO_MOEDA = "$";

    private static int indiceMoeda(String chave) {
        for (int i = 0; i < MOEDAS_TRADE.length; i++) if ((PREFIXO_MOEDA + MOEDAS_TRADE[i]).equals(chave)) return i;
        return -1;
    }

    /** Quantas dessa moeda o player tem (do jeito que a Bag mostra). */
    private long moedasTidas(int i) {
        long resto = i + 1 < VALOR_MOEDA.length ? currencyTotal % VALOR_MOEDA[i + 1] : currencyTotal;
        return resto / VALOR_MOEDA[i];
    }

    private long valorMoedasSelecionadas(String exceto) {
        long total = 0;
        for (Map.Entry<String, Integer> e : tradeSelecao.entrySet()) {
            int i = indiceMoeda(e.getKey());
            if (i >= 0 && !e.getKey().equals(exceto)) total += VALOR_MOEDA[i] * e.getValue();
        }
        return total;
    }

    /** Maximo dessa moeda que ainda cabe no saldo (contando as outras ja' escolhidas). */
    private int maxMoeda(int i, String chave) {
        long livre = Math.max(0, currencyTotal - valorMoedasSelecionadas(chave));
        return (int) Math.min(Integer.MAX_VALUE, livre / VALOR_MOEDA[i]);
    }

    private Button slotMoeda(String tipo, long quantidade, Color corQtd, boolean marcado, boolean foco) {
        Button.ButtonStyle estilo = new Button.ButtonStyle();
        estilo.up = skinDrawable(marcado ? "trade-slot-selected" : foco ? "bag-slot-selected" : "bag-slot");
        estilo.over = marcado ? estilo.up : skinDrawable("bag-slot-hover");
        estilo.down = skinDrawable("bag-slot-selected");
        Button slot = new Button(estilo);
        Stack conteudo = new Stack();
        TextureAtlas.AtlasRegion r = atlas.findRegion("ui/currency/" + tipo);
        if (r != null) {
            Image icone = new Image(new TextureRegionDrawable(r));
            icone.setScaling(Scaling.fit);
            Table moldura = new Table();
            moldura.add(icone).grow().pad(8);
            conteudo.add(moldura);
        }
        if (quantidade > 0) {
            Table marcadores = new Table();
            marcadores.bottom();
            adicionarQuantidade(marcadores, (int) Math.min(Integer.MAX_VALUE, quantidade));
            Label numero = (Label) marcadores.getChildren().peek();
            numero.setColor(corQtd);
            conteudo.add(marcadores);
        }
        slot.add(conteudo).grow().pad(2);
        return slot;
    }

    private TextButton.TextButtonStyle estiloBotaoTrade(String base) {
        TextButton.TextButtonStyle e = new TextButton.TextButtonStyle(skin.get(base, TextButton.TextButtonStyle.class));
        e.font = skin.getFont("botao-texto-font");
        return e;
    }

    private void construirPaginasTrade(Skin skin) {
        skin.add("trade-slot-selected", UiSkin.retangulo(
            new Color(0.12f, 0.24f, 0.12f, 1f), COR_TRADE_SELECIONADO, 2),
            com.badlogic.gdx.scenes.scene2d.utils.Drawable.class);
        // Mesmo visual do bank: botoes chapados, fonte sem contorno, sem
        // textos de explicacao.
        Label.LabelStyle estiloTitulo = new Label.LabelStyle(fonteBanco(), Color.WHITE);
        // Textos antigos (passos/resumo) ficam fora da tela.
        resumoTrade = new LabelPixel("", estiloTitulo);
        passoTrade = new LabelPixel("", estiloTitulo);
        passoConfirm = new LabelPixel("", estiloTitulo);

        // ---- Tela 1: escolher itens ----
        tradeGrid.top().left();
        tradeDetalhes.top().left();
        botaoEnviarTrade = botaoComIcone("Send Offer", "ui/currency/Gold", estiloPlano(COR_BOTAO_TRADE, Color.WHITE));
        // Celular: o livro ja' e' ampliado 1.2x - a fonte do botao ficava enorme.
        if (MOBILE_LIVRO) botaoEnviarTrade.getLabel().setFontScale(0.7f);
        botaoEnviarTrade.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) { enviarOfertaTrade(); }
        });
        Table esquerda = new Table();
        esquerda.top().left();
        esquerda.setBackground(UiSkin.retangulo(
            new Color(0.08f, 0.08f, 0.08f, 1f), new Color(0.35f, 0.35f, 0.35f, 1f), 1));
        esquerda.add(tradeDetalhes).grow().top().left().pad(8).row();
        esquerda.add(botaoEnviarTrade).width(COLUNA_BAG - 14f).height(44).pad(6);

        tituloTrade = new LabelPixel("", estiloTitulo);
        ScrollPane scroll = new ScrollPane(tradeGrid, skin);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        scroll.setOverscroll(false, false);
        Table topo = new Table();
        topo.add(tituloTrade).left().expandX();
        topo.add(botaoFecharTrade()).size(42);

        Table direita = new Table();
        direita.top().left();
        direita.setBackground(UiSkin.retangulo(
            new Color(0.165f, 0.165f, 0.165f, 1f), new Color(0.35f, 0.35f, 0.35f, 1f), 1));
        direita.add(topo).growX().pad(10, 12, 0, 12).row();
        direita.add(scroll).grow().pad(8, 12, 10, 12);

        tradePage.add(esquerda).width(COLUNA_BAG).growY();
        tradePage.add(direita).grow();

        // ---- Tela 2: confirmar ----
        confirmDetalhes.top().left();
        botaoAceitarTrade = botaoComIcone("Accept", "ui/Confirm", estiloPlano(COR_BOTAO_ACEITAR, Color.WHITE));
        botaoAceitarTrade.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) { aceitarTrade(); }
        });
        Table esquerda2 = new Table();
        esquerda2.top().left();
        esquerda2.setBackground(UiSkin.retangulo(
            new Color(0.08f, 0.08f, 0.08f, 1f), new Color(0.35f, 0.35f, 0.35f, 1f), 1));
        esquerda2.add(confirmDetalhes).grow().top().left().pad(8).row();
        esquerda2.add(botaoAceitarTrade).width(COLUNA_BAG - 14f).height(44).pad(6);

        tituloMinhaOferta = new LabelPixel("", estiloTitulo);
        tituloOfertaOutro = new LabelPixel("", estiloTitulo);
        statusTradeConfirm = new LabelPixel("", estiloTitulo);
        statusMeuAceite = new LabelPixel("", estiloTitulo);
        statusMeuAceite.setColor(COR_TRADE_SELECIONADO);
        gradeMinhaOferta.top().left();
        gradeOfertaOutro.top().left();
        Table linhaMeuNome = new Table();
        linhaMeuNome.add(tituloMinhaOferta).left().expandX();
        linhaMeuNome.add(statusMeuAceite).right().padRight(8);
        linhaMeuNome.add(botaoFecharTrade()).size(42);
        Table linhaNomeOutro = new Table();
        linhaNomeOutro.add(tituloOfertaOutro).left().expandX();
        linhaNomeOutro.add(statusTradeConfirm).right();

        Table direita2 = new Table();
        direita2.top().left();
        direita2.setBackground(UiSkin.retangulo(
            new Color(0.165f, 0.165f, 0.165f, 1f), new Color(0.35f, 0.35f, 0.35f, 1f), 1));
        direita2.add(linhaMeuNome).growX().pad(10, 12, 4, 12).row();
        direita2.add(rolagem(gradeMinhaOferta)).grow().pad(0, 12, 6, 12).row();
        // Linha separando a minha oferta da do outro.
        direita2.add(separadorEquip()).growX().height(1).pad(2, 12, 2, 12).row();
        direita2.add(linhaNomeOutro).growX().pad(4, 12, 4, 12).row();
        direita2.add(rolagem(gradeOfertaOutro)).grow().pad(0, 12, 10, 12);

        tradeConfirmPage.add(esquerda2).width(COLUNA_BAG).growY();
        tradeConfirmPage.add(direita2).grow();
    }

    private static final Color COR_BOTAO_TRADE = new Color(0.62f, 0.48f, 0.06f, 1f);  // amarelo
    private static final Color COR_BOTAO_ACEITAR = Color.valueOf("2e7d32");

    /** X vermelho (cancela o trade pros dois), igual o do bank. */
    private TextButton botaoFecharTrade() {
        TextButton x = new TextButton("X", estiloPlano(COR_BLOQUEADO, Color.WHITE));
        x.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) { cancelarTrade(); }
        });
        return x;
    }

    private ScrollPane rolagem(Table grade) {
        ScrollPane sp = new ScrollPane(grade, skin);
        sp.setFadeScrollBars(false);
        sp.setScrollingDisabled(true, false);
        sp.setOverscroll(false, false);
        return sp;
    }

    private Table caixaOfertaScroll(ScrollPane sp) {
        Table caixa = new Table();
        caixa.setBackground(UiSkin.retangulo(new Color(0.11f, 0.11f, 0.11f, 1f), new Color(0.24f, 0.24f, 0.24f, 1f), 1));
        caixa.add(sp).grow().pad(4);
        return caixa;
    }

    private Table caixaOferta(Table grade) {
        ScrollPane sp = new ScrollPane(grade, skin);
        sp.setFadeScrollBars(false);
        sp.setScrollingDisabled(true, false);
        sp.setOverscroll(false, false);
        Table caixa = new Table();
        caixa.setBackground(UiSkin.retangulo(new Color(0.11f, 0.11f, 0.11f, 1f), new Color(0.24f, 0.24f, 0.24f, 1f), 1));
        caixa.add(sp).grow().pad(4);
        return caixa;
    }

    public boolean emTrade() { return modoTrade; }

    /** trade_started: abre a tela 1 no lugar do livro. */
    public void abrirTrade(String outro) {
        if (modoBanco) fecharBanco();
        if (modoLoja) fecharLoja();
        modoTrade = true;
        tradeOutro = outro;
        tradeSelecao.clear();
        tradeFoco = null;
        tradeEnviado = false;
        tradeAceito = false;
        minhaOferta = null;
        ofertaOutro = null;
        secaoAntesDoTrade = secaoAtual;
        cancelarExclusao();
        if (colunaEsquerdaAbas != null) colunaEsquerdaAbas.setVisible(false);
        if (colunaDireitaAbas != null) colunaDireitaAbas.setVisible(false);
        tituloTrade.setText("Trade with " + outro);
        passoTrade.setText("Step 1 of 2  -  Tap what you want to give");
        botaoEnviarTrade.setText("Send Offer");
        botaoEnviarTrade.setDisabled(false);
        mostrarPaginaTrade(tradePage);
        atualizarGradeTrade();
        atualizarDetalhesTrade();
        root.setVisible(true);
    }

    private void mostrarPaginaTrade(Table pagina) {
        mainWindow.clearChildren();
        mainWindow.pad(1);
        mainWindow.add(pagina).grow();
        actionBar.setVisible(false);
    }

    /** Fecha as telas de trade (sem avisar o servidor) e volta o livro ao normal. */
    public void fecharTrade() {
        fecharPopupQuantidade();
        if (!modoTrade) return;
        modoTrade = false;
        tradeOutro = null;
        if (root.getStage() != null && campoQtdTrade != null && root.getStage().getKeyboardFocus() == campoQtdTrade) {
            root.getStage().setKeyboardFocus(null);
        }
        if (colunaEsquerdaAbas != null) colunaEsquerdaAbas.setVisible(true);
        if (colunaDireitaAbas != null) colunaDireitaAbas.setVisible(true);
        root.setVisible(false);
        selecionarSecao(secaoAntesDoTrade);
    }

    private void cancelarTrade() {
        if (socket.isConnected()) socket.emitRaw("trade_cancel", "{}");
        fecharTrade();
    }

    private void atualizarGradeTrade() {
        tradeGrid.clearChildren();
        int n = 0;
        int maiorMoeda = -1;
        for (int i = 0; i < VALOR_MOEDA.length; i++) if (currencyTotal >= VALOR_MOEDA[i]) maiorMoeda = i;
        for (int i = 0; i <= maiorMoeda; i++) {
            String tipo = MOEDAS_TRADE[i];
            String chave = PREFIXO_MOEDA + tipo;
            boolean marcado = tradeSelecao.containsKey(chave);
            final int indice = i;
            Button slot = slotBanco(atlas.findRegion("ui/currency/" + tipo), marcado ? tradeSelecao.get(chave) : moedasTidas(i), true,
                marcado ? COR_TRADE_SELECIONADO : corMoeda(tipo), chave.equals(tradeFoco), marcado, () -> clicarMoedaTrade(indice));
            tradeGrid.add(slot).size(SLOT_EQUIP).pad(1.5f);
            if (++n % COLUNAS_INVENTARIO == 0) tradeGrid.row();
        }
        for (InventoryItem item : inventoryItems) {
            boolean marcado = tradeSelecao.containsKey(item.instanceId);
            int qtd = marcado ? tradeSelecao.get(item.instanceId) : item.quantity;
            Button slot = slotBanco(iconeDoItem(item.itemPath), qtd, marcado && item.quantity > 1,
                marcado ? COR_TRADE_SELECIONADO : Color.WHITE, item.instanceId.equals(tradeFoco), marcado, () -> clicarItemTrade(item));
            tradeGrid.add(slot).size(SLOT_EQUIP).pad(1.5f);
            if (++n % COLUNAS_INVENTARIO == 0) tradeGrid.row();
        }
    }

    /** Icone + quantidade (escolhida/total em verde quando marcado). */
    private Stack conteudoSlot(String caminho, int quantidade, int escolhida) {
        Stack conteudo = new Stack();
        TextureAtlas.AtlasRegion textura = iconeDoItem(caminho);
        if (textura != null) {
            Image icone = new Image(new TextureRegionDrawable(textura));
            icone.setScaling(Scaling.fit);
            Table moldura = new Table();
            moldura.add(icone).grow().pad(3);
            conteudo.add(moldura);
        }
        if (quantidade > 1 || escolhida > 1) {
            Table marcadores = new Table();
            marcadores.bottom();
            adicionarQuantidade(marcadores, escolhida > 0 ? escolhida : quantidade);
            Label numero = (Label) marcadores.getChildren().peek();
            if (escolhida > 0) numero.setColor(COR_TRADE_SELECIONADO);
            conteudo.add(marcadores);
        }
        if (temCooldown(caminho)) conteudo.add(sobreposicaoCooldown(caminho));
        return conteudo;
    }

    private void clicarMoedaTrade(int i) {
        String chave = PREFIXO_MOEDA + MOEDAS_TRADE[i];
        tradeFoco = chave;
        if (!tradeEnviado) {
            if (tradeSelecao.containsKey(chave)) {
                tradeSelecao.remove(chave); // clicar no marcado desmarca direto
            } else {
                int max = maxMoeda(i, chave);
                if (max > 0) {
                    abrirPopupQuantidade(MOEDAS_TRADE[i] + " Coins", atlas.findRegion("ui/currency/" + MOEDAS_TRADE[i]), max, null, v -> {
                        tradeSelecao.put(chave, v);
                        atualizarGradeTrade();
                        atualizarDetalhesTrade();
                    });
                }
            }
        }
        atualizarGradeTrade();
        atualizarDetalhesTrade();
    }

    private void detalhesMoedaTrade(int i) {
        String tipo = MOEDAS_TRADE[i];
        String chave = PREFIXO_MOEDA + tipo;
        tradeDetalhes.clearChildren();
        tradeDetalhes.top().left();
        Label nome = new LabelPixel(tipo + " Coins", skin.get("hud", Label.LabelStyle.class));
        escalarFonte(nome, 0.74f * FONTE_STATS);
        nome.setColor(corMoeda(tipo));
        tradeDetalhes.add(nome).left().padBottom(1).row();
        if (tradeSelecao.containsKey(chave)) {
            tradeDetalhes.add(linhaStat("Selected " + tradeSelecao.get(chave), COR_TRADE_SELECIONADO)).left().row();
        }
    }

    // ---- Janelinha de quantidade (item com mais de 1 / moedas) ----
    private Table popupQuantidade;

    /** Pergunta quantos (1..max, so' numeros); Confirm chama aoConfirmar. */
    private void abrirPopupQuantidade(String titulo, int max, java.util.function.IntConsumer aoConfirmar) {
        abrirPopupQuantidade(titulo, null, max, null, aoConfirmar);
    }

    /**
     * icone: aparece do lado do nome (null = sem). cabe: com essa quantia da'
     * pra confirmar? (null = sempre) - senao o Confirm vira vermelho "No Cap"
     * (ex: sacar do bank mais do que cabe na mochila).
     */
    private void abrirPopupQuantidade(String titulo, TextureRegion icone, int max,
                                      java.util.function.IntPredicate cabe, java.util.function.IntConsumer aoConfirmar) {
        fecharPopupQuantidade();
        Stage stage = root.getStage();
        if (stage == null) return;
        Table caixa = new Table();
        caixa.setBackground(UiSkin.retangulo(COR_POPUP_FUNDO, COR_POPUP_BORDA, 2));
        caixa.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled);
        caixa.pad(16, 18, 16, 18);
        Label rotulo = new LabelPixel(titulo, skin.get("hud", Label.LabelStyle.class));
        escalarFonte(rotulo, 0.8f * FONTE_STATS);
        // Moeda: nome na cor dela (Copper laranja etc).
        for (String tipo : MOEDAS_TRADE) if (titulo.equals(tipo + " Coins")) rotulo.setColor(corMoeda(tipo));
        // Pedidos do proximo popup (loja): confirmar 2x e mostrar as moedas embaixo.
        final boolean pedeCerteza = proximoPopupPedeCerteza;
        final Table rodapeExtra = proximoPopupRodape;
        proximoPopupPedeCerteza = false;
        proximoPopupRodape = null;
        final boolean[] perguntou = {false};
        Label limite = new LabelPixel("", skin.get("hud", Label.LabelStyle.class));
        escalarFonte(limite, 0.6f * FONTE_STATS);
        limite.setColor(Color.LIGHT_GRAY);
        final BarraQuantidade[] barraQtd = new BarraQuantidade[1];
        final Runnable[] revalidar = new Runnable[1];
        com.badlogic.gdx.scenes.scene2d.ui.TextField campo = new com.badlogic.gdx.scenes.scene2d.ui.TextField(String.valueOf(max), skin);
        campo.setTextFieldFilter(new com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldFilter.DigitsOnlyFilter());
        campo.setMaxLength(String.valueOf(max).length());
        campo.setAlignment(Align.center);
        campo.setTextFieldListener((c, ch) -> {
            String t = c.getText();
            if (!t.isEmpty()) {
                int v;
                try { v = Integer.parseInt(t); } catch (NumberFormatException e) { v = max; }
                int lim = Math.max(1, Math.min(max, v));
                if (lim != v) {
                    c.setText(String.valueOf(lim));
                    c.setCursorPosition(c.getText().length());
                }
                if (barraQtd[0] != null) barraQtd[0].definir(lim, false);
            }
            revalidar[0].run();
            if (ch == '\n' || ch == '\r') confirmarPopup(c, max, cabe, aoConfirmar);
        });
        // Barrinha arrastavel (1..max) + "-"/"+" pra acertar o numero exato.
        BarraQuantidade barra = new BarraQuantidade(max, v -> {
            campo.setText(String.valueOf(v));
            campo.setCursorPosition(campo.getText().length());
            revalidar[0].run();
        });
        barraQtd[0] = barra;
        barra.definir(max, false);
        TextButton menos = botaoRepetir("-", () -> barra.definir(barra.valor - 1, true));
        TextButton mais = botaoRepetir("+", () -> barra.definir(barra.valor + 1, true));
        TextButton botaoMax = new TextButton("Max", estiloBotaoTrade("default"));
        botaoMax.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                campo.setText(String.valueOf(max));
                barra.definir(max, false);
                revalidar[0].run();
            }
        });
        TextButton cancelar = new TextButton("Cancel", estiloBotaoTrade("vermelho"));
        TextButton.TextButtonStyle estiloOk = estiloBotaoTrade("verde"), estiloSemCap = estiloBotaoTrade("vermelho");
        TextButton confirmar = new TextButton("Confirm", estiloOk);
        revalidar[0] = () -> {
            boolean ok = cabe == null || cabe.test(valorDoCampo(campo, max));
            perguntou[0] = false; // mudou a quantia: pergunta de novo
            confirmar.setStyle(ok ? estiloOk : estiloSemCap);
            confirmar.setText(ok ? "Confirm" : "No Cap");
        };
        cancelar.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) { fecharPopupQuantidade(); }
        });
        confirmar.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                if (pedeCerteza && !perguntou[0]) {
                    if (cabe != null && !cabe.test(valorDoCampo(campo, max))) return;
                    perguntou[0] = true;
                    confirmar.setText("Are you sure?");
                    return;
                }
                confirmarPopup(campo, max, cabe, aoConfirmar);
            }
        });
        Table cabecalho = new Table();
        if (icone != null) {
            Image img = new Image(new TextureRegionDrawable(icone));
            img.setScaling(Scaling.fit);
            Table moldura = new Table();
            moldura.setBackground(skinDrawable("bag-slot"));
            moldura.add(img).size(30).pad(5);
            cabecalho.add(moldura).size(44).padRight(10);
        }
        Table textos = new Table();
        textos.add(rotulo).left();
        cabecalho.add(textos).left();
        Table linhaCampo = new Table();
        linhaCampo.add(campo).width(140).height(40).padRight(6);
        linhaCampo.add(botaoMax).width(64).height(40);
        Table linhaBotoes = new Table();
        linhaBotoes.add(cancelar).width(110).height(44).padRight(10);
        linhaBotoes.add(confirmar).width(pedeCerteza ? 150 : 110).height(44);
        caixa.add(cabecalho).left().padBottom(12).row();
        caixa.add(linhaCampo).padBottom(10).row();
        if (max > 1) {
            Table linhaBarra = new Table();
            linhaBarra.add(menos).size(40).padRight(8);
            linhaBarra.add(barra).width(240).height(40);
            linhaBarra.add(mais).size(40).padLeft(8);
            caixa.add(linhaBarra).padBottom(14).row();
        }
        caixa.add(linhaBotoes);
        if (rodapeExtra != null) caixa.row().padTop(12);
        if (rodapeExtra != null) caixa.add(rodapeExtra);
        revalidar[0].run();

        popupQuantidade = new Table();
        popupQuantidade.setFillParent(true);
        popupQuantidade.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled); // bloqueia o livro atras
        popupQuantidade.setBackground(UiSkin.retangulo(new Color(0f, 0f, 0f, 0.45f), new Color(0f, 0f, 0f, 0.45f), 1));
        popupQuantidade.center();
        popupQuantidade.add(caixa);
        stage.addActor(popupQuantidade);
        stage.setKeyboardFocus(campo);
        campo.selectAll();
    }

    private boolean proximoPopupPedeCerteza = false;
    private Table proximoPopupRodape = null;

    /** "Your coins:" com as moedas que o player tem (maior primeiro). */
    private Table linhaMinhasMoedas() {
        Table t = new Table();
        Label rot = new LabelPixel("Your coins:", estiloNitido(TAM_TEXTO_RODAPE));
        rot.setColor(Color.LIGHT_GRAY);
        t.add(rot).padRight(8);
        boolean algum = false;
        for (int i = MOEDAS_TRADE.length - 1; i >= 0; i--) {
            long q = moedasTidas(i);
            if (q <= 0) continue;
            algum = true;
            TextureAtlas.AtlasRegion r = atlas.findRegion("ui/currency/" + MOEDAS_TRADE[i]);
            if (r != null) {
                Image img = new Image(new TextureRegionDrawable(r));
                img.setScaling(Scaling.fit);
                t.add(img).size(20).padRight(3);
            }
            Label n = new LabelPixel(String.valueOf(q), estiloNitido(TAM_TEXTO_RODAPE));
            n.setColor(corMoeda(MOEDAS_TRADE[i]));
            t.add(n).padRight(10);
        }
        if (!algum) t.add(new LabelPixel("0", estiloNitido(TAM_TEXTO_RODAPE)));
        return t;
    }

    private static final Color COR_POPUP_FUNDO = Color.valueOf("1c1c1c");
    private static final Color COR_POPUP_BORDA = Color.valueOf("424242");

    private static int valorDoCampo(com.badlogic.gdx.scenes.scene2d.ui.TextField campo, int max) {
        int v;
        try { v = Integer.parseInt(campo.getText()); } catch (NumberFormatException e) { v = max; }
        return Math.max(1, Math.min(max, v));
    }

    /** Botao que repete enquanto segurado (devagar no comeco, depois rapido). */
    private TextButton botaoRepetir(String texto, Runnable acao) {
        TextButton b = new TextButton(texto, estiloBotaoTrade("default"));
        b.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
            @Override public boolean touchDown(com.badlogic.gdx.scenes.scene2d.InputEvent e, float x, float y, int p, int bt) {
                acao.run();
                b.clearActions();
                b.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.sequence(
                    com.badlogic.gdx.scenes.scene2d.actions.Actions.delay(0.4f),
                    com.badlogic.gdx.scenes.scene2d.actions.Actions.forever(com.badlogic.gdx.scenes.scene2d.actions.Actions.sequence(
                        com.badlogic.gdx.scenes.scene2d.actions.Actions.run(acao),
                        com.badlogic.gdx.scenes.scene2d.actions.Actions.delay(0.05f)))));
                return true;
            }
            @Override public void touchUp(com.badlogic.gdx.scenes.scene2d.InputEvent e, float x, float y, int p, int bt) {
                b.clearActions();
            }
        });
        return b;
    }

    /**
     * Barrinha de quantidade (1..max), arrastavel. Tocar pula pro ponto e
     * arrastar leva o puxador junto (so' o X conta).
     * Desenha so' trilho, parte cheia e o puxador - nao cria nada por valor.
     */
    private final class BarraQuantidade extends com.badlogic.gdx.scenes.scene2d.ui.Widget {
        final int max;
        int valor = 1;
        private float valorExato = 1f; // fracionario durante o ajuste fino
        private final java.util.function.IntConsumer aoMudar;
        private final com.badlogic.gdx.scenes.scene2d.utils.Drawable trilho =
            UiSkin.retangulo(new Color(0.06f, 0.06f, 0.06f, 1f), new Color(0.35f, 0.35f, 0.35f, 1f), 1);
        private final com.badlogic.gdx.scenes.scene2d.utils.Drawable cheio =
            UiSkin.retangulo(new Color(0.85f, 0.65f, 0.15f, 1f), new Color(0.6f, 0.45f, 0.1f, 1f), 1);
        private final com.badlogic.gdx.scenes.scene2d.utils.Drawable puxador =
            UiSkin.retangulo(new Color(0.9f, 0.9f, 0.9f, 1f), new Color(0.2f, 0.2f, 0.2f, 1f), 2);

        BarraQuantidade(int max, java.util.function.IntConsumer aoMudar) {
            this.max = Math.max(1, max);
            this.aoMudar = aoMudar;
            // O puxador segue o X do dedo/mouse, venha o arrasto de onde
            // vier (antes, arrastar na diagonal entrava num "ajuste fino" e
            // quase nao mexia). Numero exato: "-"/"+" ou digitar.
            addListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
                @Override public boolean touchDown(com.badlogic.gdx.scenes.scene2d.InputEvent e, float x, float y, int p, int bt) {
                    if (p != 0) return false;
                    pularPara(x);
                    e.stop();
                    return true;
                }
                @Override public void touchDragged(com.badlogic.gdx.scenes.scene2d.InputEvent e, float x, float y, int p) {
                    if (p == 0) pularPara(x);
                }
            });
        }

        private float larguraUtil() { return getWidth() - getHeight() * 0.5f; }

        private void pularPara(float x) {
            float inicio = getHeight() * 0.25f;
            float t = (x - inicio) / Math.max(1f, larguraUtil());
            valorExato = 1f + Math.max(0f, Math.min(1f, t)) * (max - 1);
            aplicar(Math.round(valorExato), true, false);
        }

        /** avisar: chama aoMudar (vindo da barra/botoes; o campo de texto nao). */
        void definir(int v, boolean avisar) { aplicar(v, avisar, true); }

        private void aplicar(int v, boolean avisar, boolean sincronizarExato) {
            v = Math.max(1, Math.min(max, v));
            if (sincronizarExato) valorExato = v;
            else valorExato = Math.max(1f, Math.min(max, valorExato));
            if (v == valor) return;
            valor = v;
            if (avisar) aoMudar.accept(v);
        }

        @Override public float getPrefWidth() { return 240; }
        @Override public float getPrefHeight() { return 40; }

        @Override public void draw(com.badlogic.gdx.graphics.g2d.Batch batch, float parentAlpha) {
            validate();
            float h = getHeight(), alturaTrilho = h * 0.3f;
            float x0 = getX() + h * 0.25f, y0 = getY() + (h - alturaTrilho) / 2f;
            float w = larguraUtil();
            float t = max > 1 ? (valor - 1f) / (max - 1f) : 1f;
            batch.setColor(1f, 1f, 1f, parentAlpha);
            trilho.draw(batch, x0, y0, w, alturaTrilho);
            if (t > 0f) cheio.draw(batch, x0, y0, Math.max(4f, w * t), alturaTrilho);
            float lado = h * 0.7f;
            puxador.draw(batch, x0 + w * t - lado * 0.35f, getY() + (h - lado) / 2f, lado * 0.7f, lado);
        }
    }

    private void confirmarPopup(com.badlogic.gdx.scenes.scene2d.ui.TextField campo, int max,
                                java.util.function.IntPredicate cabe, java.util.function.IntConsumer aoConfirmar) {
        int v = valorDoCampo(campo, max);
        if (cabe != null && !cabe.test(v)) return; // "No Cap": nao faz nada
        fecharPopupQuantidade();
        aoConfirmar.accept(v);
    }

    private void fecharPopupQuantidade() {
        if (popupQuantidade == null) return;
        Stage stage = popupQuantidade.getStage();
        if (stage != null) stage.setKeyboardFocus(null);
        popupQuantidade.remove();
        popupQuantidade = null;
    }

    /** Maximo de itens (slots) numa oferta - igual servidor.py::TRADE_MAX_ITENS. Moedas nao contam. */
    private static final int TRADE_MAX_ITENS = 10;
    private boolean tradeLimiteAviso = false;

    private int itensNaOferta() {
        int n = 0;
        for (String k : tradeSelecao.keySet()) if (indiceMoeda(k) < 0) n++;
        return n;
    }

    private void clicarItemTrade(InventoryItem item) {
        tradeFoco = item.instanceId;
        tradeLimiteAviso = false;
        if (!tradeEnviado) {
            if (tradeSelecao.containsKey(item.instanceId)) {
                tradeSelecao.remove(item.instanceId); // clicar no marcado desmarca direto
            } else if (itensNaOferta() >= TRADE_MAX_ITENS) {
                tradeLimiteAviso = true; // ja' tem 10: nao entra mais
            } else if (item.quantity > 1) {
                abrirPopupQuantidade(nomeExibicao(item.itemPath), iconeDoItem(item.itemPath), item.quantity, null, v -> {
                    tradeSelecao.put(item.instanceId, v);
                    atualizarGradeTrade();
                    atualizarDetalhesTrade();
                });
            } else {
                tradeSelecao.put(item.instanceId, 1);
            }
        }
        atualizarGradeTrade();
        atualizarDetalhesTrade();
    }

    private InventoryItem itemPorId(String id) {
        if (id == null) return null;
        for (InventoryItem it : inventoryItems) if (it.instanceId.equals(id)) return it;
        return null;
    }

    private void atualizarResumoTrade() {
        if (resumoTrade == null) return;
        int itens = 0;
        for (String k : tradeSelecao.keySet()) if (indiceMoeda(k) < 0) itens++;
        long moedas = valorMoedasSelecionadas(null);
        if (itens == 0 && moedas == 0) {
            resumoTrade.setText("Your offer: nothing yet");
            resumoTrade.setColor(COR_PASSO);
            return;
        }
        StringBuilder sb = new StringBuilder("Your offer: ");
        if (itens > 0) sb.append(itens).append(itens == 1 ? " item" : " items");
        if (moedas > 0) sb.append(itens > 0 ? " + " : "").append(textoMoedas(moedas));
        resumoTrade.setText(sb.toString());
        resumoTrade.setColor(COR_TRADE_SELECIONADO);
    }

    /** "1g 20s 5c" (so' as que tem). */
    private static String textoMoedas(long total) {
        String[] sufixo = {"c", "s", "g", "p"};
        StringBuilder sb = new StringBuilder();
        for (int i = MOEDAS_TRADE.length - 1; i >= 0; i--) {
            long q = total / VALOR_MOEDA[i];
            total -= q * VALOR_MOEDA[i];
            if (q > 0) sb.append(sb.length() > 0 ? " " : "").append(q).append(sufixo[i]);
        }
        return sb.length() == 0 ? "0c" : sb.toString();
    }

    private void atualizarDetalhesTrade() {
        atualizarResumoTrade();
        int moeda = indiceMoeda(tradeFoco);
        if (moeda >= 0) {
            detalhesMoedaTrade(moeda);
            return;
        }
        InventoryItem item = itemPorId(tradeFoco);
        if (item == null) {
            tradeDetalhes.clearChildren();
            return;
        }
        preencherBlocoStats(tradeDetalhes, item.itemPath, null, item.quantity);
        if (tradeLimiteAviso) tradeDetalhes.add(linhaStat("Max " + TRADE_MAX_ITENS + " items", Color.valueOf("ff6a6a"))).left().row();
        if (item.quantity > 1) tradeDetalhes.add(linhaStat("Quantity " + item.quantity, Color.LIGHT_GRAY)).left().row();
        if (tradeSelecao.containsKey(item.instanceId) && item.quantity > 1) {
            tradeDetalhes.add(linhaStat("Selected " + tradeSelecao.get(item.instanceId), COR_TRADE_SELECIONADO)).left().row();
        }
    }

    private void enviarOfertaTrade() {
        if (tradeEnviado || !socket.isConnected()) return;
        fecharPopupQuantidade();
        // Campo vazio na hora de mandar: vale a quantia que ja' estava.
        String payload = GameSocket.obj(w -> {
            w.array("items");
            for (Map.Entry<String, Integer> e : tradeSelecao.entrySet()) {
                if (indiceMoeda(e.getKey()) >= 0) continue;
                InventoryItem it = itemPorId(e.getKey());
                if (it == null) continue;
                w.object();
                w.set("instance_id", e.getKey());
                w.set("qty", Math.max(1, Math.min(it.quantity, e.getValue())));
                w.pop();
            }
            w.pop();
            w.set("currency", Math.min(currencyTotal, valorMoedasSelecionadas(null)));
        });
        socket.emitRaw("trade_offer_update", payload);
        socket.emitRaw("trade_ready", "{}");
        tradeEnviado = true;
        botaoEnviarTrade.setText("Waiting...");
        passoTrade.setText("Waiting for " + tradeOutro + "...");
        botaoEnviarTrade.setDisabled(true);
        if (root.getStage() != null && campoQtdTrade != null && root.getStage().getKeyboardFocus() == campoQtdTrade) {
            root.getStage().setKeyboardFocus(null);
        }
        atualizarDetalhesTrade();
    }

    /** trade_offer_updated: guarda as duas ofertas (pra tela 2). */
    public void atualizarOfertasTrade(JsonValue dados) {
        if (dados == null) return;
        minhaOferta = dados.get("self_offer");
        ofertaOutro = dados.get("other_offer");
        if (modoTrade && tradeConfirmPage.getParent() != null) montarConfirmacaoTrade();
    }

    /** trade_advanced_to_confirm: os dois mandaram - abre a tela 2. */
    public void mostrarConfirmacaoTrade(String meuNome) {
        if (!modoTrade) return;
        tradeAceito = false;
        botaoAceitarTrade.setText("Accept");
        botaoAceitarTrade.setDisabled(false);
        statusTradeConfirm.setText("");
        statusMeuAceite.setText("");
        tituloMinhaOferta.setText("You give");
        tituloMinhaOferta.setColor(Color.valueOf("ff9a6a"));
        tituloOfertaOutro.setText("You receive");
        tituloOfertaOutro.setColor(COR_TRADE_SELECIONADO);
        tradeFoco = null;
        mostrarPaginaTrade(tradeConfirmPage);
        montarConfirmacaoTrade();
    }

    private void montarConfirmacaoTrade() {
        montarGradeOferta(gradeMinhaOferta, minhaOferta, "Nothing offered.");
        montarGradeOferta(gradeOfertaOutro, ofertaOutro, "Nothing offered.");
        if (tradeFoco == null) {
            confirmDetalhes.clearChildren();
        }
    }

    private void montarGradeOferta(Table grade, JsonValue oferta, String vazioTexto) {
        grade.clearChildren();
        JsonValue itens = oferta != null ? oferta.get("items") : null;
        int n = 0;
        long moeda = oferta != null ? oferta.getLong("currency", 0L) : 0L;
        for (int i = MOEDAS_TRADE.length - 1; i >= 0 && moeda > 0; i--) {
            long qtd = moeda / VALOR_MOEDA[i];
            if (qtd <= 0) continue;
            moeda -= qtd * VALOR_MOEDA[i];
            String tipo = MOEDAS_TRADE[i];
            String chave = (grade == gradeMinhaOferta ? "eu" : "ele") + PREFIXO_MOEDA + tipo;
            Button slot = slotBanco(atlas.findRegion("ui/currency/" + tipo), qtd, true, corMoeda(tipo), chave.equals(tradeFoco), () -> {
                tradeFoco = chave;
                confirmDetalhes.clearChildren();
                confirmDetalhes.top().left();
                Label nome = new LabelPixel(tipo + " Coins", skin.get("hud", Label.LabelStyle.class));
                escalarFonte(nome, 0.74f * FONTE_STATS);
                nome.setColor(corMoeda(tipo));
                confirmDetalhes.add(nome).left().padBottom(1).row();
                montarGradeOferta(gradeMinhaOferta, minhaOferta, vazioTexto);
                montarGradeOferta(gradeOfertaOutro, ofertaOutro, vazioTexto);
            });
            grade.add(slot).size(SLOT_EQUIP).pad(1.5f);
            if (++n % COLUNAS_INVENTARIO == 0) grade.row();
        }
        if (itens != null) {
            for (JsonValue e = itens.child; e != null; e = e.next) {
                String caminho = e.getString("item", "");
                int qtd = Math.max(1, e.getInt("qty", 1));
                String chave = (grade == gradeMinhaOferta ? "eu:" : "ele:") + e.getString("instance_id", "" + n);
                Button slot = slotBanco(iconeDoItem(caminho), qtd, false, Color.WHITE, chave.equals(tradeFoco), () -> {
                    tradeFoco = chave;
                    preencherBlocoStats(confirmDetalhes, caminho, null, qtd);
                    if (qtd > 1) confirmDetalhes.add(linhaStat("Quantity " + qtd, Color.LIGHT_GRAY)).left().row();
                    montarGradeOferta(gradeMinhaOferta, minhaOferta, vazioTexto);
                    montarGradeOferta(gradeOfertaOutro, ofertaOutro, vazioTexto);
                });
                grade.add(slot).size(SLOT_EQUIP).pad(1.5f);
                n++;
                if (n % COLUNAS_INVENTARIO == 0) grade.row();
            }
        }
    }

    private void aceitarTrade() {
        if (tradeAceito || !socket.isConnected()) return;
        tradeAceito = true;
        socket.emitRaw("trade_lock_in", "{}");
        botaoAceitarTrade.setText("Waiting...");
        botaoAceitarTrade.setDisabled(true);
    }

    /** trade_lock_state_updated. */
    public void estadoAceiteTrade(boolean euAceitei, boolean eleAceitou) {
        if (!modoTrade) return;
        statusTradeConfirm.setText(eleAceitou ? "Accepted" : "");
        statusTradeConfirm.setColor(COR_TRADE_SELECIONADO);
        statusMeuAceite.setText(euAceitei ? "Accepted" : "");
    }

    // ===================== BANK (NPC Jack) =====================
    // Abre no lugar do livro (abas laterais escondidas, igual o trade). Em
    // cima, duas abas: Bag (o que da' pra guardar) e Bank (o que esta'
    // guardado, compartilhado entre os personagens da conta). Clicar num
    // item mostra os detalhes na esquerda e o botao Deposit/Withdraw; com
    // mais de 1 (pilha ou moeda) abre a janelinha de quantidade. Sacar mais
    // do que cabe na mochila: botao vermelho "No Cap". Tudo validado no
    // servidor (servidor.py, BANK).

    private boolean modoBanco = false;
    private boolean abaBancoBank = false; // false = Bag, true = Bank
    private String secaoAntesDoBanco = "Bag";
    private final Table bankPage = new Table();
    private final Table bancoGrid = new Table();
    private final Table bancoDetalhes = new Table();
    private Table bancoRodapeAcao;
    private TextButton bancoAcao, abaBag, abaBank;
    private TextButton.TextButtonStyle estiloBancoOk, estiloBancoBloqueado;
    private Label bancoInfo, bancoAviso;
    private final List<InventoryItem> itensBanco = new ArrayList<>();
    private long moedasBanco = 0L;
    private int slotsBanco = 100;
    private String bancoFoco = null; // instanceId / id do bank / "$Copper"...

    private static final Color COR_ABA_ATIVA = Color.valueOf("2f2f2f");
    private static final Color COR_ABA_INATIVA = Color.valueOf("1c1c1c");
    private static final Color COR_ABA_BORDA = Color.valueOf("424242");

    public boolean emBanco() { return modoBanco; }

    // Abas e botoes do bank: chapados, fonte sem contorno (a do chat).
    private static final Color COR_ABA_BAG = Color.valueOf("e07a1f"), COR_ABA_BAG_OFF = Color.valueOf("5a3410");
    private static final Color COR_ABA_BANK = Color.valueOf("8a5a32"), COR_ABA_BANK_OFF = Color.valueOf("3e2a18");
    private static final Color COR_BOTAO_BANCO = Color.valueOf("3a3a3a");
    private static final Color COR_BLOQUEADO = Color.valueOf("a82828");
    private static final String ICONE_ABA_BAG = "sheet/r58_c6", ICONE_ABA_BANK = "sheet/r60_c6", ICONE_DEPOSITO = "sheet/r81_c1", ICONE_SAQUE = "sheet/r80_c1";
    private Image iconeAcaoBanco;

    private com.badlogic.gdx.graphics.g2d.BitmapFont fonteBanco() {
        return skin.get("chat-log", Label.LabelStyle.class).font;
    }

    private TextButton.TextButtonStyle estiloPlano(Color cor, Color corTexto) {
        TextButton.TextButtonStyle e = new TextButton.TextButtonStyle();
        e.font = skin.getFont("botao-texto-font");
        e.fontColor = corTexto;
        e.up = UiSkin.retangulo(cor, cor.cpy().lerp(Color.BLACK, 0.35f), 1);
        e.over = UiSkin.retangulo(cor.cpy().lerp(Color.WHITE, 0.1f), cor.cpy().lerp(Color.BLACK, 0.35f), 1);
        e.down = UiSkin.retangulo(cor.cpy().lerp(Color.BLACK, 0.15f), cor.cpy().lerp(Color.BLACK, 0.35f), 1);
        return e;
    }

    private TextButton.TextButtonStyle estiloAba(boolean bank, boolean ativa) {
        Color cor = bank ? (ativa ? COR_ABA_BANK : COR_ABA_BANK_OFF) : (ativa ? COR_ABA_BAG : COR_ABA_BAG_OFF);
        return estiloPlano(cor, ativa ? Color.WHITE : new Color(0.7f, 0.7f, 0.7f, 1f));
    }

    /** Mostra/esconde o icone de um botaoComIcone (sem deixar o espaco vazio). */
    private static void iconeDoBotao(TextButton b, boolean mostrar) {
        if (b.getChildren().size == 0 || !(b.getChildren().first() instanceof Image)) return;
        com.badlogic.gdx.scenes.scene2d.ui.Cell<?> c = b.getCell(b.getChildren().first());
        if (c == null) return;
        c.size(mostrar ? 24 : 0).padRight(mostrar ? 4 : 0);
        b.getChildren().first().setVisible(mostrar);
        b.invalidate();
    }

    /** Botao com icone (16px do atlas em 2x) antes do texto. */
    private TextButton botaoComIcone(String texto, String regiao, TextButton.TextButtonStyle estilo) {
        TextButton b = new TextButton(texto, estilo);
        b.clearChildren();
        TextureAtlas.AtlasRegion r = atlas.findRegion(regiao);
        if (r != null) {
            Image img = new Image(new TextureRegionDrawable(r));
            img.setScaling(Scaling.fit);
            b.add(img).size(24).padRight(4);
        }
        b.add(b.getLabel());
        return b;
    }

    /** Barrinha cinza com "usado/maximo" no meio (Capacity e slots do bank). */
    private static final class BarraUso extends com.badlogic.gdx.scenes.scene2d.ui.Widget {
        float fracao;
        private final com.badlogic.gdx.scenes.scene2d.utils.Drawable trilho =
            UiSkin.retangulo(Color.valueOf("1c1c1c"), Color.valueOf("424242"), 1);
        private final com.badlogic.gdx.scenes.scene2d.utils.Drawable cheio =
            UiSkin.retangulo(Color.valueOf("5a5a5a"), Color.valueOf("5a5a5a"), 1);
        @Override public float getPrefHeight() { return 22; }
        @Override public void draw(com.badlogic.gdx.graphics.g2d.Batch batch, float parentAlpha) {
            batch.setColor(1f, 1f, 1f, parentAlpha);
            trilho.draw(batch, getX(), getY(), getWidth(), getHeight());
            float w = (getWidth() - 2f) * Math.max(0f, Math.min(1f, fracao));
            if (w >= 1f) cheio.draw(batch, getX() + 1f, getY() + 1f, w, getHeight() - 2f);
        }
    }

    private BarraUso barraUsoBanco;

    private void construirPaginaBanco(Skin skin) {
        bancoGrid.top().left();
        bancoDetalhes.top().left();

        estiloBancoOk = estiloPlano(COR_BOTAO_BANCO, Color.WHITE);
        estiloBancoBloqueado = estiloPlano(COR_BLOQUEADO, Color.WHITE);
        bancoAcao = botaoComIcone("Withdraw", ICONE_DEPOSITO, estiloBancoOk);
        iconeAcaoBanco = (Image) bancoAcao.getChildren().first();
        bancoAcao.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) { acaoBanco(); }
        });
        bancoRodapeAcao = new Table();
        // Largura fixa (a da coluna): "Withdraw" e' mais largo e empurrava a borda.
        bancoRodapeAcao.add(bancoAcao).width(COLUNA_BAG - 14f).height(44);

        Table esquerda = new Table();
        esquerda.top().left();
        esquerda.setBackground(UiSkin.retangulo(
            new Color(0.08f, 0.08f, 0.08f, 1f), new Color(0.35f, 0.35f, 0.35f, 1f), 1));
        esquerda.add(bancoDetalhes).grow().top().left().pad(8).row();
        esquerda.add(bancoRodapeAcao).growX().pad(6);

        abaBag = botaoComIcone("Bag", ICONE_ABA_BAG, estiloAba(false, true));
        abaBank = botaoComIcone("Bank", ICONE_ABA_BANK, estiloAba(true, false));
        abaBag.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) { trocarAbaBanco(false); }
        });
        abaBank.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) { trocarAbaBanco(true); }
        });
        TextButton fechar = new TextButton("X", estiloPlano(COR_BLOQUEADO, Color.WHITE));
        fechar.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) { setVisible(false); }
        });
        Table abas = new Table();
        abas.add(abaBag).width(112).height(42).padRight(4);
        abas.add(abaBank).width(112).height(42);
        abas.add().expandX();
        abas.add(fechar).size(42);

        ScrollPane scroll = new ScrollPane(bancoGrid, skin);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        scroll.setOverscroll(false, false);

        bancoInfo = new LabelPixel("", estiloNitido(TAM_TEXTO_CAP));
        bancoInfo.setAlignment(Align.center);
        bancoAviso = new LabelPixel("", new Label.LabelStyle(fonteBanco(), Color.valueOf("ff6a6a")));
        escalarFonte(bancoAviso, 0.8f);
        barraUsoBanco = new BarraUso();
        Table textoBarra = new Table();
        textoBarra.add(bancoInfo).center();
        Stack barra = new Stack(barraUsoBanco, textoBarra);

        Table direita = new Table();
        direita.top().left();
        direita.setBackground(UiSkin.retangulo(
            new Color(0.165f, 0.165f, 0.165f, 1f), new Color(0.35f, 0.35f, 0.35f, 1f), 1));
        direita.add(abas).growX().pad(10, 12, 0, 12).row();
        direita.add(scroll).grow().pad(8, 12, 4, 12).row();
        direita.add(bancoAviso).right().pad(0, 12, 2, 12).row();
        direita.add(barra).growX().height(24).pad(0, 12, 10, 12);

        bankPage.add(esquerda).width(COLUNA_BAG).growY();
        bankPage.add(direita).grow();
    }

    /** bank_opened: abre a tela do bank no lugar do livro. */
    public void abrirBanco() {
        if (modoTrade) return;
        if (!modoBanco) secaoAntesDoBanco = secaoAtual;
        modoBanco = true;
        bancoFoco = null;
        bancoAviso.setText("");
        cancelarExclusao();
        if (colunaEsquerdaAbas != null) colunaEsquerdaAbas.setVisible(false);
        if (colunaDireitaAbas != null) colunaDireitaAbas.setVisible(false);
        mainWindow.clearChildren();
        mainWindow.pad(1);
        mainWindow.add(bankPage).grow();
        actionBar.setVisible(false);
        trocarAbaBanco(false);
        root.setVisible(true);
    }

    /** Fecha a tela do bank e volta o livro ao normal (escondido). */
    public void fecharBanco() {
        fecharPopupQuantidade();
        if (!modoBanco) return;
        modoBanco = false;
        if (colunaEsquerdaAbas != null) colunaEsquerdaAbas.setVisible(true);
        if (colunaDireitaAbas != null) colunaDireitaAbas.setVisible(true);
        root.setVisible(false);
        selecionarSecao(secaoAntesDoBanco);
    }

    private void trocarAbaBanco(boolean bank) {
        abaBancoBank = bank;
        abaBag.setStyle(estiloAba(false, !bank));
        abaBank.setStyle(estiloAba(true, bank));
        bancoFoco = null;
        bancoAviso.setText("");
        atualizarTelaBanco(); atualizarTelaLoja();
    }

    /** bank_synced: conteudo do bank (e aviso "full"/"no_cap", se teve). */
    public void atualizarBanco(JsonValue dados) {
        if (dados == null) return;
        itensBanco.clear();
        JsonValue itens = dados.get("items");
        if (itens != null) {
            for (JsonValue e = itens.child; e != null; e = e.next) {
                String caminho = e.getString("item", "");
                if (caminho.isEmpty()) continue;
                itensBanco.add(new InventoryItem(e.getString("id", ""), caminho, Math.max(1, e.getInt("qty", 1)), false));
            }
        }
        moedasBanco = Math.max(0L, dados.getLong("currency", 0L));
        slotsBanco = Math.max(1, dados.getInt("slots_max", 100));
        String msg = dados.getString("msg", "");
        bancoAviso.setText("full".equals(msg) ? "The bank is full." : "no_cap".equals(msg) ? "Not enough capacity." : "");
        atualizarTelaBanco(); atualizarTelaLoja();
    }

    private void atualizarTelaBanco() {
        if (!modoBanco) return;
        // Foco que sumiu (item saiu da lista / moeda zerou) sai.
        if (bancoFoco != null && indiceMoeda(bancoFoco) < 0 && itemDoBanco(bancoFoco) == null) bancoFoco = null;
        if (bancoFoco != null && indiceMoeda(bancoFoco) >= 0 && maxMoedaBanco(indiceMoeda(bancoFoco)) <= 0) bancoFoco = null;
        atualizarGradeBanco();
        atualizarDetalhesBanco();
        if (abaBancoBank) {
            bancoInfo.setText(itensBanco.size() + "/" + slotsBanco);
            barraUsoBanco.fracao = itensBanco.size() / (float) slotsBanco;
        } else {
            bancoInfo.setText(formatarPeso(capacityUsed) + "/" + formatarPeso(capacityMaximum));
            barraUsoBanco.fracao = capacityMaximum > 0f ? capacityUsed / capacityMaximum : 0f;
        }
    }

    private long saldoDaAba() { return abaBancoBank ? moedasBanco : currencyTotal; }

    private long moedasDaAba(int i) {
        long total = saldoDaAba();
        long resto = i + 1 < VALOR_MOEDA.length ? total % VALOR_MOEDA[i + 1] : total;
        return resto / VALOR_MOEDA[i];
    }

    /** Maximo dessa moeda que da' pra mover (troca as maiores, igual o trade). */
    private int maxMoedaBanco(int i) {
        return (int) Math.min(Integer.MAX_VALUE, saldoDaAba() / VALOR_MOEDA[i]);
    }

    private List<InventoryItem> itensDaAba() { return abaBancoBank ? itensBanco : inventoryItems; }

    private InventoryItem itemDoBanco(String id) {
        if (id == null) return null;
        for (InventoryItem it : itensDaAba()) if (it.instanceId.equals(id)) return it;
        return null;
    }

    /** Slot do bank: icone em 3x (48px), quantidade no canto. */
    private Button slotBanco(TextureRegion icone, long qtd, boolean mostrarUm, Color corQtd, boolean foco, Runnable aoClicar) {
        return slotBanco(icone, qtd, mostrarUm, corQtd, foco, false, aoClicar);
    }

    /** marcado: no trade, item ja' na oferta (borda verde). */
    private Button slotBanco(TextureRegion icone, long qtd, boolean mostrarUm, Color corQtd, boolean foco, boolean marcado, Runnable aoClicar) {
        Button.ButtonStyle estilo = new Button.ButtonStyle();
        estilo.up = skinDrawable(marcado ? "trade-slot-selected" : foco ? "bag-slot-selected" : "bag-slot");
        estilo.over = foco ? estilo.up : skinDrawable("bag-slot-hover");
        estilo.down = skinDrawable("bag-slot-selected");
        Button slot = new Button(estilo);
        Stack conteudo = new Stack();
        if (icone != null) {
            Image img = new Image(new TextureRegionDrawable(icone));
            img.setScaling(Scaling.fit);
            Table moldura = new Table();
            moldura.add(img).size(38);
            conteudo.add(moldura);
        }
        if (qtd > 1 || (mostrarUm && qtd == 1)) {
            Table marcadores = new Table();
            marcadores.bottom();
            adicionarQuantidade(marcadores, (int) Math.min(Integer.MAX_VALUE, qtd));
            ((Label) marcadores.getChildren().peek()).setColor(corQtd);
            conteudo.add(marcadores);
        }
        slot.add(conteudo).grow().pad(1);
        slot.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) { aoClicar.run(); }
        });
        return slot;
    }

    private void atualizarGradeBanco() {
        bancoGrid.clearChildren();
        int n = 0;
        int maiorMoeda = -1;
        for (int i = 0; i < VALOR_MOEDA.length; i++) if (saldoDaAba() >= VALOR_MOEDA[i]) maiorMoeda = i;
        for (int i = 0; i <= maiorMoeda; i++) {
            String tipo = MOEDAS_TRADE[i];
            String chave = PREFIXO_MOEDA + tipo;
            Button slot = slotBanco(atlas.findRegion("ui/currency/" + tipo), moedasDaAba(i), true,
                corMoeda(tipo), chave.equals(bancoFoco), () -> {
                    bancoFoco = chave;
                    bancoAviso.setText("");
                    atualizarTelaBanco(); atualizarTelaLoja();
                });
            bancoGrid.add(slot).size(SLOT_EQUIP).pad(1.5f);
            if (++n % COLUNAS_INVENTARIO == 0) bancoGrid.row();
        }
        for (InventoryItem item : itensDaAba()) {
            Button slot = slotBanco(iconeDoItem(item.itemPath), item.quantity, false, Color.WHITE,
                item.instanceId.equals(bancoFoco), () -> {
                    bancoFoco = item.instanceId;
                    bancoAviso.setText("");
                    atualizarTelaBanco(); atualizarTelaLoja();
                });
            bancoGrid.add(slot).size(SLOT_EQUIP).pad(1.5f);
            if (++n % COLUNAS_INVENTARIO == 0) bancoGrid.row();
        }
    }

    private float capLivre() { return Math.max(0f, capacityMaximum - capacityUsed); }

    private float capUnitario(String caminho) {
        EquipStats d = ITEM_STATS.get(caminho);
        return d == null ? 0f : d.cap;
    }

    private boolean empilhavel(String caminho) {
        EquipStats d = ITEM_STATS.get(caminho);
        return d != null && d.empilhavel;
    }

    /** Quantos desse item cabem na mochila agora. */
    private boolean cabeNaMochila(String caminho, int qtd) {
        return capUnitario(caminho) * qtd <= capLivre() + 0.0001f;
    }

    private boolean bancoCheioPara(String caminho) {
        if (itensBanco.size() < slotsBanco) return false;
        if (!empilhavel(caminho)) return true;
        for (InventoryItem it : itensBanco) if (it.itemPath.equals(caminho)) return false;
        return true;
    }

    private void atualizarDetalhesBanco() {
        bancoDetalhes.clearChildren();
        bancoDetalhes.top().left();
        int moeda = indiceMoeda(bancoFoco);
        InventoryItem item = moeda >= 0 ? null : itemDoBanco(bancoFoco);
        if (moeda < 0 && item == null) {
            bancoRodapeAcao.setVisible(false);
            return;
        }
        bancoRodapeAcao.setVisible(true);
        boolean bloqueado = false;
        String textoBloqueado = "";
        if (moeda >= 0) {
            String tipo = MOEDAS_TRADE[moeda];
            Label nome = new LabelPixel(tipo + " Coins", skin.get("hud", Label.LabelStyle.class));
            escalarFonte(nome, 0.74f * FONTE_STATS);
            nome.setColor(corMoeda(tipo));
            bancoDetalhes.add(nome).left().padBottom(1).row();
        } else {
            preencherBlocoStats(bancoDetalhes, item.itemPath, null, item.quantity);
            if (item.quantity > 1) bancoDetalhes.add(linhaStat("Quantity " + item.quantity, Color.LIGHT_GRAY)).left().row();
            if (abaBancoBank) {
                bloqueado = !cabeNaMochila(item.itemPath, 1);
                textoBloqueado = "No Cap";
            } else {
                bloqueado = bancoCheioPara(item.itemPath);
                textoBloqueado = "Bank Full";
            }
        }
        bancoAcao.setStyle(bloqueado ? estiloBancoBloqueado : estiloBancoOk);
        bancoAcao.setText(bloqueado ? textoBloqueado : abaBancoBank ? "Withdraw" : "Deposit");
        TextureAtlas.AtlasRegion r = atlas.findRegion(abaBancoBank ? ICONE_SAQUE : ICONE_DEPOSITO);
        if (iconeAcaoBanco != null && r != null) iconeAcaoBanco.setDrawable(new TextureRegionDrawable(r));
    }

    private void acaoBanco() {
        if (!modoBanco || !socket.isConnected()) return;
        int moeda = indiceMoeda(bancoFoco);
        String direcao = abaBancoBank ? "withdraw" : "deposit";
        if (moeda >= 0) {
            int max = maxMoedaBanco(moeda);
            if (max <= 0) return;
            long valor = VALOR_MOEDA[moeda];
            TextureAtlas.AtlasRegion icone = atlas.findRegion("ui/currency/" + MOEDAS_TRADE[moeda]);
            abrirPopupQuantidade(MOEDAS_TRADE[moeda] + " Coins", icone, max, null, v ->
                socket.emitRaw("bank_currency_move", GameSocket.obj(w -> {
                    w.set("direction", direcao);
                    w.set("amount", v * valor);
                })));
            return;
        }
        InventoryItem item = itemDoBanco(bancoFoco);
        if (item == null) return;
        String evento = abaBancoBank ? "bank_withdraw" : "bank_deposit";
        String chaveId = abaBancoBank ? "id" : "instance_id";
        if (abaBancoBank && !cabeNaMochila(item.itemPath, 1)) return;
        if (!abaBancoBank && bancoCheioPara(item.itemPath)) return;
        if (item.quantity > 1) {
            java.util.function.IntPredicate cabe = abaBancoBank ? v -> cabeNaMochila(item.itemPath, v) : null;
            abrirPopupQuantidade(nomeExibicao(item.itemPath), iconeDoItem(item.itemPath), item.quantity, cabe, v ->
                socket.emitRaw(evento, GameSocket.obj(w -> { w.set(chaveId, item.instanceId); w.set("qty", v); })));
        } else {
            socket.emitRaw(evento, GameSocket.obj(w -> { w.set(chaveId, item.instanceId); w.set("qty", 1); }));
        }
    }

    // ===================== LOJA DOS NPCS (Syron...) =====================
    // Abre no lugar do livro (igual o bank). Aba Buy: os itens do NPC com o
    // preco no slot; clicar mostra os detalhes e o botao Buy (quantidade:
    // o maximo que da' pra pagar e que cabe na mochila). Aba Sell: embaixo os
    // meus itens que o NPC compra, em cima o que vou vender; clicar embaixo
    // sobe (com quantidade se for pilha), clicar em cima volta. Tudo validado
    // no servidor (servidor.py, LOJA DOS NPCS).

    private static final class ItemLoja {
        final String item;
        final int preco, venda;
        ItemLoja(String item, int preco, int venda) { this.item = item; this.preco = preco; this.venda = venda; }
    }

    private boolean modoLoja = false, abaLojaVenda = false;
    private String lojaNpc = "", secaoAntesDaLoja = "Bag";
    private final List<ItemLoja> itensLoja = new ArrayList<>();
    private final Table lojaPage = new Table();
    private final Table lojaDetalhes = new Table();
    private final Table lojaGridCompra = new Table();
    private final Table lojaGridVendendo = new Table();
    private final Table lojaGridMeus = new Table();
    private final Map<String, Integer> vendaSelecao = new LinkedHashMap<>(); // instanceId -> qty
    private String lojaFoco = null; // caminho (Buy) ou instanceId (Sell)
    private TextButton lojaAcao, abaBuy, abaSell, botaoVender;
    private Table lojaRodapeAcao, conteudoCompra, conteudoVenda;
    private com.badlogic.gdx.scenes.scene2d.ui.Container<Table> lojaConteudo;
    private Label lojaAviso, lojaInfo, lojaTotal;
    private BarraUso barraCapLoja;
    private com.badlogic.gdx.scenes.scene2d.ui.Container<Table> moedasLoja;
    private Image iconeTotalLoja;
    private com.badlogic.gdx.scenes.scene2d.ui.Container<Table> totalLoja;

    // Buy: marrom claro (cor de pele).
    private static final Color COR_ABA_BUY = new Color(0.62f, 0.48f, 0.06f, 1f), COR_ABA_BUY_OFF = new Color(0.28f, 0.22f, 0.04f, 1f);
    private static final Color COR_BOTAO_COMPRAR = Color.valueOf("b9825a"); // botao Buy de baixo: cor de pele
    private static final String ICONE_COMPRAR = "sheet/r64_c5"; // 64,688 no atlas
    private boolean compraArmada = false; // 1o clique no Buy (1 unidade): "Are you sure?"
    private static final Color COR_ABA_SELL = Color.valueOf("2e7d32"), COR_ABA_SELL_OFF = Color.valueOf("173d19");

    public boolean emLoja() { return modoLoja; }

    // Aba Buy de cada vendedor (cor e icone): comida amarela com a coxinha,
    // pocoes vermelha com a Basic Health Pot.
    private String lojaPapel = "";
    private static final Color COR_ABA_BUY_POT = Color.valueOf("a82828"), COR_ABA_BUY_POT_OFF = Color.valueOf("4a1414");

    private static final Color COR_ABA_BUY_ARMA = Color.valueOf("3f5f8a"), COR_ABA_BUY_ARMA_OFF = Color.valueOf("1c2a3d");
    private static final Color COR_ABA_BUY_MAGIA = Color.valueOf("7a3fa8"), COR_ABA_BUY_MAGIA_OFF = Color.valueOf("33194a");

    private static final String ICONE_APRENDER = "sheet/r51_c10"; // 64,624: botao Learn da Nyla

    private boolean lojaDeMagia() { return "spell".equals(lojaPapel); }

    private String textoComprar() { return lojaDeMagia() ? "Learn" : "Buy"; }

    private String iconeAbaBuy() {
        if ("pot".equals(lojaPapel)) return "sheet/r44_c2";
        if ("weapon".equals(lojaPapel)) return "sheet/r23_c5"; // Outlander Helm
        if ("spell".equals(lojaPapel)) return "sheet/r61_c0";  // 160,672
        return ICONE_FULLNESS;
    }

    private TextButton.TextButtonStyle estiloAbaLoja(boolean venda, boolean ativa) {
        boolean pot = "pot".equals(lojaPapel), arma = "weapon".equals(lojaPapel), magia = "spell".equals(lojaPapel);
        Color cor = venda ? (ativa ? COR_ABA_SELL : COR_ABA_SELL_OFF)
            : pot ? (ativa ? COR_ABA_BUY_POT : COR_ABA_BUY_POT_OFF)
            : arma ? (ativa ? COR_ABA_BUY_ARMA : COR_ABA_BUY_ARMA_OFF)
            : magia ? (ativa ? COR_ABA_BUY_MAGIA : COR_ABA_BUY_MAGIA_OFF) : (ativa ? COR_ABA_BUY : COR_ABA_BUY_OFF);
        return estiloPlano(cor, ativa ? Color.WHITE : new Color(0.7f, 0.7f, 0.7f, 1f));
    }

    private void construirPaginaLoja() {
        lojaDetalhes.top().left();
        lojaGridCompra.top().left();
        lojaGridVendendo.top().left();
        lojaGridMeus.top().left();

        lojaAcao = botaoComIcone("Buy", ICONE_COMPRAR, estiloPlano(COR_BOTAO_COMPRAR, Color.WHITE));
        lojaAcao.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) { comprarFoco(); }
        });
        lojaRodapeAcao = new Table();
        lojaRodapeAcao.add(lojaAcao).width(COLUNA_BAG - 14f).height(44);
        Table esquerda = new Table();
        esquerda.top().left();
        esquerda.setBackground(UiSkin.retangulo(
            new Color(0.08f, 0.08f, 0.08f, 1f), new Color(0.35f, 0.35f, 0.35f, 1f), 1));
        // Largura fixa: texto comprido nos detalhes nao empurra o botao pra fora.
        esquerda.add(lojaDetalhes).width(COLUNA_BAG - 16f).growY().top().left().pad(8).row();
        esquerda.add(lojaRodapeAcao).width(COLUNA_BAG - 14f).pad(6, 7, 7, 7);

        abaBuy = botaoComIcone("Buy", ICONE_FULLNESS, estiloAbaLoja(false, true));
        abaSell = botaoComIcone("Sell", "ui/currency/Gold", estiloAbaLoja(true, false));
        abaBuy.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) { trocarAbaLoja(false); }
        });
        abaSell.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) { trocarAbaLoja(true); }
        });
        TextButton fechar = new TextButton("X", estiloPlano(COR_BLOQUEADO, Color.WHITE));
        fechar.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) { setVisible(false); }
        });
        Table abas = new Table();
        abas.add(abaBuy).width(112).height(42).padRight(4);
        abas.add(abaSell).width(112).height(42);
        abas.add().expandX();
        abas.add(fechar).size(42);

        Label.LabelStyle estiloTexto = new Label.LabelStyle(fonteBanco(), Color.WHITE);
        lojaAviso = new LabelPixel("", new Label.LabelStyle(fonteBanco(), Color.valueOf("ff6a6a")));
        lojaAviso.setFontScale(0.8f);

        // ---- Buy: grade + barra de capacidade ----
        lojaInfo = new LabelPixel("", estiloNitido(TAM_TEXTO_CAP));
        barraCapLoja = new BarraUso();
        Table textoBarra = new Table();
        textoBarra.add(lojaInfo).center();
        conteudoCompra = new Table();
        conteudoCompra.top();
        conteudoCompra.add(rolagem(lojaGridCompra)).grow().row();
        conteudoCompra.add(lojaAviso).right().padTop(2).row();
        moedasLoja = new com.badlogic.gdx.scenes.scene2d.ui.Container<>();
        moedasLoja.left();
        // Linha das moedas: na loja de magia o botao Learn fica aqui, na direita.
        botaoAprenderRodape = botaoComIcone("Learn", ICONE_APRENDER, estiloPlano(COR_BOTAO_COMPRAR, Color.WHITE));
        botaoAprenderRodape.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) { aprenderMagiaFoco(); }
        });
        Table linhaMoedas = new Table();
        linhaMoedas.add(moedasLoja).left().expandX();
        linhaMoedas.add(botaoAprenderRodape).height(40).minWidth(130).right();
        conteudoCompra.add(linhaMoedas).growX().padTop(2).row();
        conteudoCompra.add(new Stack(barraCapLoja, textoBarra)).growX().height(24).padTop(4);

        // ---- Sell: em cima o que vai vender, embaixo os meus itens ----
        Label tituloVendendo = new LabelPixel("Selling", estiloTexto);
        Label tituloMeus = new LabelPixel("Your items", estiloTexto);
        tituloVendendo.setColor(Color.LIGHT_GRAY);
        tituloMeus.setColor(Color.LIGHT_GRAY);
        lojaTotal = new LabelPixel("0", new Label.LabelStyle(fonteBanco(), Color.WHITE));
        iconeTotalLoja = new Image();
        iconeTotalLoja.setScaling(Scaling.fit);
        botaoVender = botaoComIcone("Sell", "ui/currency/Gold", estiloPlano(COR_ABA_SELL, Color.WHITE));
        botaoVender.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) { venderSelecao(); }
        });
        Table rodapeVenda = new Table();
        totalLoja = new com.badlogic.gdx.scenes.scene2d.ui.Container<>();
        totalLoja.left();
        rodapeVenda.add(totalLoja).left().expandX();
        rodapeVenda.add(botaoVender).width(150).height(40);
        conteudoVenda = new Table();
        conteudoVenda.top();
        conteudoVenda.add(tituloVendendo).left().row();
        conteudoVenda.add(rolagem(lojaGridVendendo)).growX().height(SLOT_EQUIP * 2 + 8).row();
        conteudoVenda.add(separadorEquip()).growX().height(1).pad(4, 0, 4, 0).row();
        conteudoVenda.add(tituloMeus).left().row();
        conteudoVenda.add(rolagem(lojaGridMeus)).grow().row();
        conteudoVenda.add(rodapeVenda).growX().padTop(6);

        lojaConteudo = new com.badlogic.gdx.scenes.scene2d.ui.Container<>();
        lojaConteudo.fill();
        Table direita = new Table();
        direita.top().left();
        direita.setBackground(UiSkin.retangulo(
            new Color(0.165f, 0.165f, 0.165f, 1f), new Color(0.35f, 0.35f, 0.35f, 1f), 1));
        direita.add(abas).growX().pad(10, 12, 0, 12).row();
        direita.add(lojaConteudo).grow().pad(8, 12, 10, 12);

        lojaPage.add(esquerda).width(COLUNA_BAG).growY();
        lojaPage.add(direita).grow();
    }

    /** shop_opened: {npc_id, name, items: [{item, price, sell}]}. */
    public void abrirLoja(JsonValue dados) { abrirLoja(dados, ""); }

    /** papel: funcao do NPC (food, pot...) - muda a cor/icone da aba Buy. */
    public void abrirLoja(JsonValue dados, String papel) {
        if (modoTrade || dados == null) return;
        lojaPapel = papel == null ? "" : papel;
        if (abaBuy.getChildren().size > 0 && abaBuy.getChildren().first() instanceof Image) {
            TextureAtlas.AtlasRegion r = atlas.findRegion(iconeAbaBuy());
            if (r != null) ((Image) abaBuy.getChildren().first()).setDrawable(new TextureRegionDrawable(r));
        }
        // Loja de magia: "Learn" em cima e embaixo, com icones proprios.
        abaBuy.setText(textoComprar());
        if (lojaAcao.getChildren().size > 0 && lojaAcao.getChildren().first() instanceof Image) {
            TextureAtlas.AtlasRegion r = atlas.findRegion(lojaDeMagia() ? ICONE_APRENDER : ICONE_COMPRAR);
            if (r != null) ((Image) lojaAcao.getChildren().first()).setDrawable(new TextureRegionDrawable(r));
        }
        if (modoBanco) fecharBanco();
        lojaNpc = dados.getString("npc_id", "");
        itensLoja.clear();
        JsonValue itens = dados.get("items");
        if (itens != null) for (JsonValue e = itens.child; e != null; e = e.next) {
            itensLoja.add(new ItemLoja(e.getString("item", ""), e.getInt("price", 0), e.getInt("sell", 0)));
        }
        if (!modoLoja) secaoAntesDaLoja = secaoAtual;
        modoLoja = true;
        vendaSelecao.clear();
        cancelarExclusao();
        if (colunaEsquerdaAbas != null) colunaEsquerdaAbas.setVisible(false);
        if (colunaDireitaAbas != null) colunaDireitaAbas.setVisible(false);
        mainWindow.clearChildren();
        mainWindow.pad(1);
        mainWindow.add(lojaPage).grow();
        actionBar.setVisible(false);
        trocarAbaLoja(false);
        root.setVisible(true);
    }

    public String npcDaLoja() { return modoLoja ? lojaNpc : null; }

    public void fecharLoja() {
        fecharPopupQuantidade();
        if (!modoLoja) return;
        modoLoja = false;
        vendaSelecao.clear();
        if (colunaEsquerdaAbas != null) colunaEsquerdaAbas.setVisible(true);
        if (colunaDireitaAbas != null) colunaDireitaAbas.setVisible(true);
        root.setVisible(false);
        selecionarSecao(secaoAntesDaLoja);
    }

    private void trocarAbaLoja(boolean venda) {
        abaLojaVenda = venda;
        magiaArmada = null;
        abaBuy.setStyle(estiloAbaLoja(false, !venda));
        abaSell.setStyle(estiloAbaLoja(true, venda));
        lojaFoco = null;
        lojaAviso.setText("");
        lojaConteudo.setActor(venda ? conteudoVenda : conteudoCompra);
        atualizarTelaLoja();
        atualizarBotaoAprender();
    }

    /** shop_result: dinheiro novo e o que houve (no_money/no_cap/bought/sold). */
    public void resultadoLoja(JsonValue dados) {
        if (dados == null) return;
        atualizarMoedas(dados.getLong("currency", currencyTotal));
        String r = dados.getString("result", "");
        if (!modoLoja) return;
        if ("sold".equals(r)) vendaSelecao.clear();
        lojaAviso.setText("no_money".equals(r) ? "Not enough money." : "no_cap".equals(r) ? "Not enough capacity."
            : "");
        atualizarTelaLoja();
    }

    /** Quanto o NPC paga por 1 unidade (igual servidor.py::preco_venda_npc);
     * qualquer loja compra qualquer item com preco. 0 = nao vende. */
    private int precoVenda(String caminho) {
        ItemLoja daLoja = itemLoja(caminho);
        if (daLoja != null) return daLoja.venda;
        EquipStats dados = ITEM_STATS.get(caminho);
        if (dados != null && dados.precoVendaFixo > 0) return dados.precoVendaFixo;
        return dados == null || dados.preco <= 0 ? 0 : Math.max(1, dados.preco / 2);
    }

    private ItemLoja itemLoja(String caminho) {
        for (ItemLoja i : itensLoja) if (i.item.equals(caminho)) return i;
        return null;
    }

    private void atualizarTelaLoja() {
        if (!modoLoja || lojaGridCompra == null) return;
        // Selecao de venda: tira o que sumiu da bag e corta o que passou da quantia.
        vendaSelecao.entrySet().removeIf(e -> itemPorId(e.getKey()) == null);
        for (Map.Entry<String, Integer> e : vendaSelecao.entrySet()) {
            InventoryItem it = itemPorId(e.getKey());
            if (it != null && e.getValue() > it.quantity) e.setValue(it.quantity);
        }
        if (abaLojaVenda) atualizarGradesVenda(); else atualizarGradeCompra();
        atualizarDetalhesLoja();
        lojaInfo.setText(formatarPeso(capacityUsed) + "/" + formatarPeso(capacityMaximum));
        if (moedasLoja != null) moedasLoja.setActor(linhaMinhasMoedas());
        barraCapLoja.fracao = capacityMaximum > 0f ? capacityUsed / capacityMaximum : 0f;
    }

    /** Maior moeda que cabe no valor (4550c -> Silver). */
    private static int maiorMoeda(long valor) {
        for (int i = VALOR_MOEDA.length - 1; i > 0; i--) if (valor >= VALOR_MOEDA[i]) return i;
        return 0;
    }

    /** Valor nessa moeda: inteiro se der certinho, senao com 1 casa (4550c em Silver = "45.5"). */
    private static String valorNaMoeda(long valor, int moeda) {
        long inteiro = valor / VALOR_MOEDA[moeda];
        long resto = valor % VALOR_MOEDA[moeda];
        if (resto == 0) return String.valueOf(inteiro);
        long decimo = resto * 10 / VALOR_MOEDA[moeda];
        return inteiro + "." + decimo;
    }

    /** Moeda pra mostrar um preco: a maior que divide certinho (100 = 1 Silver). */
    private int moedaDoPreco(long preco) {
        for (int i = VALOR_MOEDA.length - 1; i > 0; i--) if (preco >= VALOR_MOEDA[i] && preco % VALOR_MOEDA[i] == 0) return i;
        return 0;
    }

    /** Slot com o icone do item e, embaixo, a moedinha + o preco. */
    private Button slotComPreco(String caminho, long preco, int qtd, boolean foco, boolean marcado, Runnable aoClicar) {
        Button.ButtonStyle estilo = new Button.ButtonStyle();
        estilo.up = skinDrawable(marcado ? "trade-slot-selected" : foco ? "bag-slot-selected" : "bag-slot");
        estilo.over = foco || marcado ? estilo.up : skinDrawable("bag-slot-hover");
        estilo.down = skinDrawable("bag-slot-selected");
        Button slot = new Button(estilo);
        Stack conteudo = new Stack();
        TextureAtlas.AtlasRegion icone = iconeDoItem(caminho);
        if (icone != null) {
            Image img = new Image(new TextureRegionDrawable(icone));
            img.setScaling(Scaling.fit);
            Table moldura = new Table();
            moldura.top();
            moldura.add(img).size(40).padTop(1);
            conteudo.add(moldura);
        }
        if (preco > 0) {
            int m = maiorMoeda(preco);
            Table linhaPreco = new Table();
            linhaPreco.bottom().left();
            TextureAtlas.AtlasRegion r = atlas.findRegion("ui/currency/" + MOEDAS_TRADE[m]);
            if (r != null) {
                Image moeda = new Image(new TextureRegionDrawable(r));
                moeda.setScaling(Scaling.fit);
                linhaPreco.add(moeda).size(20).padLeft(2).padRight(2);
            }
            Label numero = new LabelPixel(valorNaMoeda(preco, m),
                new Label.LabelStyle(UiSkin.fontePreco(skin), Color.WHITE));
            numero.setColor(corMoeda(MOEDAS_TRADE[m]));
            linhaPreco.add(numero).padBottom(numero.getStyle().font.getDescent());
            conteudo.add(linhaPreco);
        }
        if (qtd > 1) {
            Table marcadores = new Table();
            marcadores.top().right();
            Label n = rotuloQuantidade(qtd);
            if (marcado) n.setColor(COR_TRADE_SELECIONADO);
            marcadores.add(n).padRight(2);
            conteudo.add(marcadores);
        }
        slot.add(conteudo).grow().pad(1);
        slot.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) { aoClicar.run(); }
        });
        return slot;
    }

    private void atualizarGradeCompra() {
        lojaGridCompra.clearChildren();
        if (lojaFoco != null && ehMagia(lojaFoco) && conheceMagia(lojaFoco)) lojaFoco = null; // aprendeu: escolhe outra
        int n = 0;
        for (ItemLoja it : itensLoja) {
            if (ehMagia(it.item) && conheceMagia(it.item)) continue; // aprendida: sai da loja
            if (ehMagia(it.item)) {
                // Magia: gaveta (nome, mana, descricao, dano e preco) em vez do slot.
                if (lojaFoco == null) lojaFoco = it.item; // ja' vem uma selecionada
                lojaGridCompra.add(gavetaMagia(it.item, it.item.equals(lojaFoco), () -> {
                    if (!it.item.equals(lojaFoco)) magiaArmada = null;
                    lojaFoco = it.item;
                    lojaAviso.setText("");
                    atualizarTelaLoja();
                }, it.preco)).growX().padBottom(6).colspan(COLUNAS_INVENTARIO).row();
                n = 0;
                continue;
            }
            Button slot = slotComPreco(it.item, it.preco, 0, it.item.equals(lojaFoco), false, () -> {
                lojaFoco = it.item;
                lojaAviso.setText("");
                atualizarTelaLoja();
            });
            lojaGridCompra.add(slot).size(SLOT_EQUIP).pad(1.5f);
            if (++n % COLUNAS_INVENTARIO == 0) lojaGridCompra.row();
        }
        atualizarBotaoAprender();
    }

    private void atualizarGradesVenda() {
        lojaGridVendendo.clearChildren();
        lojaGridMeus.clearChildren();
        int nCima = 0, nBaixo = 0;
        long total = 0;
        for (InventoryItem item : inventoryItems) {
            int venda = precoVenda(item.itemPath);
            if (venda <= 0) continue; // item sem preco: nenhum NPC compra
            int escolhido = vendaSelecao.getOrDefault(item.instanceId, 0);
            if (escolhido > 0) {
                total += (long) venda * escolhido;
                Button cima = slotComPreco(item.itemPath, (long) venda * escolhido, escolhido, item.instanceId.equals(lojaFoco), true, () -> {
                    vendaSelecao.remove(item.instanceId); // clicar em cima volta pra baixo
                    lojaFoco = item.instanceId;
                    atualizarTelaLoja();
                });
                lojaGridVendendo.add(cima).size(SLOT_EQUIP).pad(1.5f);
                if (++nCima % COLUNAS_INVENTARIO == 0) lojaGridVendendo.row();
            }
            int resta = item.quantity - escolhido;
            if (resta <= 0) continue;
            Button baixo = slotComPreco(item.itemPath, venda, resta, item.instanceId.equals(lojaFoco), false, () -> {
                lojaFoco = item.instanceId;
                if (resta > 1) {
                    abrirPopupQuantidade(nomeExibicao(item.itemPath), iconeDoItem(item.itemPath), resta, null, v -> {
                        vendaSelecao.merge(item.instanceId, v, Integer::sum);
                        atualizarTelaLoja();
                    });
                } else {
                    vendaSelecao.merge(item.instanceId, 1, Integer::sum);
                }
                atualizarTelaLoja();
            });
            lojaGridMeus.add(baixo).size(SLOT_EQUIP).pad(1.5f);
            if (++nBaixo % COLUNAS_INVENTARIO == 0) lojaGridMeus.row();
        }
        // Total convertido: 16400c = 1 Gold 64 Silver (cada moeda com o icone).
        Table linhaTotal = new Table();
        long resto = total;
        boolean algum = false;
        for (int i = VALOR_MOEDA.length - 1; i >= 0; i--) {
            long q = resto / VALOR_MOEDA[i];
            resto -= q * VALOR_MOEDA[i];
            if (q <= 0) continue;
            algum = true;
            TextureAtlas.AtlasRegion rm = atlas.findRegion("ui/currency/" + MOEDAS_TRADE[i]);
            if (rm != null) {
                Image img = new Image(new TextureRegionDrawable(rm));
                img.setScaling(Scaling.fit);
                linhaTotal.add(img).size(22).padRight(4);
            }
            Label n = new LabelPixel(String.valueOf(q), new Label.LabelStyle(fonteBanco(), Color.WHITE));
            n.setColor(corMoeda(MOEDAS_TRADE[i]));
            linhaTotal.add(n).padRight(10);
        }
        if (!algum) linhaTotal.add(new LabelPixel("0", new Label.LabelStyle(fonteBanco(), Color.WHITE)));
        totalLoja.setActor(linhaTotal);
        botaoVender.setDisabled(total <= 0);
        if (vendaArmada) {
            vendaArmada = false;
            iconeDoBotao(botaoVender, true);
            botaoVender.setText("Sell");
        }
        botaoVender.setColor(total > 0 ? Color.WHITE : new Color(1f, 1f, 1f, 0.45f));
    }

    /** Quantos desse item da' pra comprar: dinheiro e capacidade. */
    private int maxCompra(ItemLoja it) {
        if (it.preco <= 0) return 0;
        // Magia: aprende 1 vez (nao ocupa cap).
        if (ehMagia(it.item)) return conheceMagia(it.item) || currencyTotal < it.preco ? 0 : 1;
        long porDinheiro = currencyTotal / it.preco;
        float unit = capUnitario(it.item);
        long porCap = unit > 0f ? (long) Math.floor((capLivre() + 0.0001f) / unit) : Long.MAX_VALUE;
        return (int) Math.max(0, Math.min(1000, Math.min(porDinheiro, porCap)));
    }

    private void atualizarDetalhesLoja() {
        lojaDetalhes.clearChildren();
        lojaDetalhes.top().left();
        lojaRodapeAcao.setVisible(false);
        if (lojaFoco == null) return;
        if (!abaLojaVenda) {
            ItemLoja it = itemLoja(lojaFoco);
            if (it == null) return;
            preencherBlocoStats(lojaDetalhes, it.item, null);
            lojaRodapeAcao.setVisible(true);
            int max = maxCompra(it);
            boolean semDinheiro = currencyTotal < it.preco;
            boolean bloqueado = max <= 0;
            compraArmada = false;
            iconeDoBotao(lojaAcao, true);
            lojaAcao.setStyle(bloqueado ? estiloBancoBloqueado : estiloPlano(COR_BOTAO_COMPRAR, Color.WHITE));
            lojaAcao.setText(!bloqueado ? textoComprar() : semDinheiro ? "No Money" : "No Cap");
        } else {
            InventoryItem item = itemPorId(lojaFoco);
            if (item == null || precoVenda(item.itemPath) <= 0) return;
            preencherBlocoStats(lojaDetalhes, item.itemPath, null, item.quantity);
        }
    }

    private void comprarFoco() {
        if (!modoLoja || abaLojaVenda || !socket.isConnected()) return;
        ItemLoja it = itemLoja(lojaFoco);
        if (it == null) return;
        int max = maxCompra(it);
        if (max <= 0) return;
        String npc = lojaNpc;
        java.util.function.IntConsumer comprar = v -> socket.emitRaw("shop_buy", GameSocket.obj(w -> {
            w.set("npc_id", npc);
            w.set("item", it.item);
            w.set("qty", v);
        }));
        // Equipamento (nao empilha): ninguem compra 2 capacetes - vai direto no
        // "Are you sure?" do botao, 1 unidade. Janelinha de quantidade so' pra
        // pocao/comida/flecha.
        EquipStats dadosCompra = ITEM_STATS.get(it.item);
        boolean umSo = max == 1 || dadosCompra == null || !dadosCompra.empilhavel;
        if (umSo) {
            // Sem janelinha: o proprio botao pede confirmacao.
            if (!compraArmada) {
                compraArmada = true;
                iconeDoBotao(lojaAcao, false); // "Are you sure?" nao cabe com o icone
                lojaAcao.setText("Are you sure?");
                return;
            }
            compraArmada = false;
            iconeDoBotao(lojaAcao, true);
            lojaAcao.setText(textoComprar());
            comprar.accept(1);
            return;
        }
        proximoPopupPedeCerteza = true;
        proximoPopupRodape = linhaMinhasMoedas();
        abrirPopupQuantidade(nomeExibicao(it.item), iconeDoItem(it.item), max, null, comprar);
    }

    private boolean vendaArmada = false; // 1o clique no Sell: "Are you sure?"

    private void venderSelecao() {
        if (!modoLoja || vendaSelecao.isEmpty() || !socket.isConnected()) return;
        if (!vendaArmada) {
            vendaArmada = true;
            iconeDoBotao(botaoVender, false);
            botaoVender.setText("Are you sure?");
            return;
        }
        vendaArmada = false;
        iconeDoBotao(botaoVender, true);
        botaoVender.setText("Sell");
        String npc = lojaNpc;
        String payload = GameSocket.obj(w -> {
            w.set("npc_id", npc);
            w.array("items");
            for (Map.Entry<String, Integer> e : vendaSelecao.entrySet()) {
                w.object();
                w.set("instance_id", e.getKey());
                w.set("qty", e.getValue());
                w.pop();
            }
            w.pop();
        });
        socket.emitRaw("shop_sell", payload);
    }

    // ===================== PARTY =====================
    // Igual o mock "tela desejada 3": lider sempre no topo (linha mais clara,
    // icone Leader), depois os membros e, embaixo, os convites recebidos
    // (com Confirm/Negate). Cada linha: bolinha online, nome, "Lv N", icone
    // da classe e o X (lider expulsa os outros; cada um sai pelo X da propria
    // linha). Rodape: bonus de XP da party (servidor.py::_calcular_bonus_percentual)
    // e Create/Leave. Tudo validado no servidor (servidor.py, PARTY SYSTEM).

    public static final class MembroParty {
        public final String nome, classe;
        public final int level;
        MembroParty(String nome, String classe, int level) {
            this.nome = nome;
            this.classe = classe;
            this.level = level;
        }
    }

    public static final int PARTY_MAX = 6; // servidor.py::PARTY_MAX_SIZE

    private final Table partyPage = new Table();
    private final Table listaParty = new Table();
    private Label bonusParty;
    private TextButton botaoCriarParty;
    private String liderParty = null; // null = sem party
    private final List<MembroParty> membrosParty = new ArrayList<>();
    private int bonusPartyPct = 0;
    // Convites recebidos: quem convidou -> level dele.
    private final Map<String, Integer> convitesParty = new LinkedHashMap<>();
    private Runnable aoMudarParty;

    private static final Color COR_LV = new Color(0.55f, 0.55f, 0.55f, 1f);
    private static final Color COR_BONUS = Color.valueOf("f5e02a");

    private void construirPaginaParty(Skin skin) {
        partyPage.top();
        listaParty.top();
        com.badlogic.gdx.scenes.scene2d.ui.ScrollPane scroll = new com.badlogic.gdx.scenes.scene2d.ui.ScrollPane(listaParty, skin);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        Table caixa = new Table();
        caixa.setBackground(UiSkin.retangulo(new Color(0.08f, 0.08f, 0.08f, 1f), new Color(0.2f, 0.2f, 0.2f, 1f), 1));
        caixa.top();
        caixa.add(scroll).grow().pad(4);

        // Botao rosa do mock.
        TextButton.TextButtonStyle estiloCriar = new TextButton.TextButtonStyle(skin.get("vermelho", TextButton.TextButtonStyle.class));
        estiloCriar.font = skin.getFont("botao-texto-font");
        Color rosa = Color.valueOf("ee1f5f"), rosaBorda = Color.valueOf("9c0f3a");
        estiloCriar.up = UiSkin.retangulo(rosa, rosaBorda, 2);
        estiloCriar.over = UiSkin.retangulo(rosa.cpy().lerp(Color.WHITE, 0.15f), rosaBorda, 2);
        estiloCriar.down = UiSkin.retangulo(rosa.cpy().mul(0.75f, 0.75f, 0.75f, 1f), rosaBorda, 2);
        botaoCriarParty = new TextButton("Create", estiloCriar);
        botaoCriarParty.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                if (!socket.isConnected()) return;
                if (liderParty == null) socket.emitRaw("create_party", "{}");
                else sairDaParty();
            }
        });
        bonusParty = new LabelPixel("", skin.get("hud", Label.LabelStyle.class));
        escalarFonte(bonusParty, 0.6f);
        bonusParty.setColor(COR_BONUS);

        Table rodape = new Table();
        rodape.add(bonusParty).expandX().left().bottom();
        rodape.add(botaoCriarParty).width(110).height(44);
        partyPage.add(caixa).grow().row();
        partyPage.add(rodape).growX().height(44).padTop(8);
        reconstruirListaParty();
    }

    private void sairDaParty() {
        if (liderParty == null || !socket.isConnected() || nomeLocal == null) return;
        socket.emitRaw("kick_party_member", GameSocket.obj(w -> w.set("target_name", nomeLocal)));
    }

    private void reconstruirListaParty() {
        listaParty.clearChildren();
        boolean souLider = nomeLocal != null && nomeLocal.equals(liderParty);
        // Lider primeiro, depois a ordem de entrada (a do servidor).
        List<MembroParty> ordenados = new ArrayList<>(membrosParty);
        ordenados.sort((a, b) -> a.nome.equals(liderParty) == b.nome.equals(liderParty) ? 0
            : (a.nome.equals(liderParty) ? -1 : 1));
        for (MembroParty m : ordenados) {
            boolean lider = m.nome.equals(liderParty);
            boolean eu = m.nome.equals(nomeLocal);
            // X: o lider expulsa qualquer um; cada um sai pela propria linha.
            Runnable acaoX = eu ? this::sairDaParty
                : souLider ? () -> {
                    if (socket.isConnected()) socket.emitRaw("kick_party_member", GameSocket.obj(w -> w.set("target_name", m.nome)));
                } : null;
            listaParty.add(linhaParty(m.nome, m.level, m.classe, lider, acaoX, null)).growX().height(40).padBottom(3).row();
        }
        for (Map.Entry<String, Integer> c : convitesParty.entrySet()) {
            String quem = c.getKey();
            listaParty.add(linhaParty(quem, c.getValue(), null, false,
                () -> responderConviteParty(quem, false),
                () -> responderConviteParty(quem, true))).growX().height(40).padBottom(3).row();
        }
        if (ordenados.isEmpty() && convitesParty.isEmpty()) {
            Label vazio = new LabelPixel("You are not in a party.", skin.get("hud", Label.LabelStyle.class));
            escalarFonte(vazio, 0.6f);
            listaParty.add(vazio).pad(10);
        }
        bonusParty.setText("Class EXP Bonus: " + bonusPartyPct + "%");
        botaoCriarParty.setText(liderParty == null ? "Create" : "Leave");
    }

    /** classe null = linha de convite (sem bolinha/icone de classe, com Confirm). */
    private Table linhaParty(String nome, int level, String classe, boolean lider, Runnable acaoX, Runnable acaoConfirmar) {
        Table linha = new Table();
        Color fundo = lider ? new Color(0.27f, 0.27f, 0.27f, 1f) : new Color(0.17f, 0.17f, 0.17f, 1f);
        linha.setBackground(UiSkin.retangulo(fundo, new Color(0.12f, 0.12f, 0.12f, 1f), 1));
        linha.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled);
        if (classe != null) linha.add(iconeAtlas("ui/OnlineIcon", 26f)).size(26f).padLeft(8).padRight(8);
        Label rotulo = new LabelPixel(nome, skin.get("hud", Label.LabelStyle.class));
        escalarFonte(rotulo, 0.75f);
        linha.add(rotulo).left().padLeft(classe != null ? 0 : 8);
        Label lv = new LabelPixel("Lv " + level, skin.get("hud", Label.LabelStyle.class));
        escalarFonte(lv, 0.6f);
        lv.setColor(COR_LV);
        linha.add(lv).left().padLeft(16).expandX();
        if (lider) linha.add(iconeAtlas("ui/party/Leader", 26f)).size(26f).padRight(4);
        if (classe != null) linha.add(iconeAtlas(iconeClasse(classe), 26f)).size(26f).padRight(4);
        if (acaoConfirmar != null) linha.add(botaoIconeParty("ui/Confirm", acaoConfirmar)).size(30f).padRight(2);
        // Sem X (nao sou lider, linha de outro): os icones encostam na direita.
        if (acaoX != null) linha.add(botaoIconeParty("ui/Negate", acaoX)).size(30f).padRight(6);
        else linha.getCells().peek().padRight(10);
        // Clicar na gaveta (membro ou convite) abre a janela do player, igual Friends.
        linha.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            @Override public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                if (aoClicarAmigo != null && !nome.equals(nomeLocal)) aoClicarAmigo.accept(nome);
            }
        });
        return linha;
    }

    private Button botaoIconeParty(String regiao, Runnable acao) {
        Button b = new Button(new Button.ButtonStyle());
        b.add(iconeAtlas(regiao, 26f)).size(26f);
        b.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            @Override public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                event.stop(); // nao abre a janela do player da linha
                acao.run();
            }
        });
        return b;
    }

    private void responderConviteParty(String quem, boolean aceitar) {
        convitesParty.remove(quem);
        if (socket.isConnected()) {
            socket.emitRaw(aceitar ? "accept_party_invite" : "decline_party_invite",
                GameSocket.obj(w -> w.set("inviter_name", quem)));
        }
        reconstruirListaParty();
    }

    /** Nome do proprio personagem (pra saber qual linha e' a minha). */
    public void definirNomeLocal(String nome) {
        nomeLocal = nome;
        reconstruirListaParty();
    }

    /** party_update do servidor. */
    public void atualizarParty(JsonValue dados) {
        membrosParty.clear();
        liderParty = null;
        bonusPartyPct = 0;
        if (dados != null) {
            liderParty = dados.getString("leader_name", "");
            if (liderParty.isEmpty()) liderParty = null;
            bonusPartyPct = dados.getInt("bonus_percent", 0);
            JsonValue lista = dados.get("members");
            if (lista != null) {
                for (JsonValue m = lista.child; m != null; m = m.next) {
                    String nome = m.getString("name", "");
                    if (nome.isEmpty()) continue;
                    membrosParty.add(new MembroParty(nome, m.getString("class_name", "Knight"), m.getInt("level", 1)));
                    convitesParty.remove(nome); // ja' esta comigo
                }
            }
        }
        reconstruirListaParty();
        if (aoMudarParty != null) aoMudarParty.run();
    }

    /** party_disbanded: saiu/foi expulso/a party acabou. */
    public void limparParty() {
        membrosParty.clear();
        liderParty = null;
        bonusPartyPct = 0;
        reconstruirListaParty();
        if (aoMudarParty != null) aoMudarParty.run();
    }

    /** party_invite_received: vira uma linha com Confirm/Negate. */
    public void adicionarConviteParty(String quem, int level) {
        if (quem == null || quem.isEmpty()) return;
        convitesParty.remove(quem);
        convitesParty.put(quem, level);
        reconstruirListaParty();
    }

    public void removerConviteParty(String quem) {
        if (convitesParty.remove(quem) != null) reconstruirListaParty();
    }

    public String liderParty() { return liderParty; }

    /** Ele me convidou pra party (convite ainda pendente). */
    public boolean temConviteParty(String nome) { return convitesParty.containsKey(nome); }

    public List<MembroParty> membrosParty() { return membrosParty; }

    public boolean estaNaParty(String nome) {
        for (MembroParty m : membrosParty) if (m.nome.equals(nome)) return true;
        return false;
    }

    public void definirAoMudarParty(Runnable r) { aoMudarParty = r; }

    /** Abre o livro direto numa aba (ex: clicar na notificacao de convite). */
    public void abrirSecao(String secao) {
        setVisible(true);
        selecionarSecao(secao);
    }

    // ===================== FRIENDS =====================
    // Lista de amigos (servidor.py::get_friends_list): online primeiro, depois
    // ordem alfabetica. Cada linha: bolinha On/Off, nome e, na direita, os
    // motivos marcados (Seller/Guild/Pk) e o icone da classe.

    public static final class AmigoInfo {
        public final String nome;
        public String classe;
        public boolean online, pk, guild, seller;
        /** Skin salva no servidor (aparece mesmo com ele offline). */
        public JsonValue skins;
        AmigoInfo(String nome) { this.nome = nome; }
    }

    private final Map<String, AmigoInfo> amigos = new LinkedHashMap<>();
    private final Table listaAmigos = new Table();
    private final Table rodapeAmigos = new Table();
    private final Table rodapeAdicionar = new Table();
    private com.badlogic.gdx.scenes.scene2d.ui.TextField campoNovoAmigo;
    private Label statusAmigos;
    private String amigoSelecionado = null; // (sem uso visual: clicar abre a janela do player)
    private Runnable aoMudarAmigos; // avisa quem mostra a janela do jogador
    private java.util.function.Consumer<String> aoClicarAmigo; // abre a janela do player
    private boolean modoRemover = false; // "Remove": o proximo clique numa linha remove
    private String nomeLocal = null;

    private void construirPaginaFriends(Skin skin) {
        friendsPage.top();
        listaAmigos.top();
        com.badlogic.gdx.scenes.scene2d.ui.ScrollPane scroll = new com.badlogic.gdx.scenes.scene2d.ui.ScrollPane(listaAmigos, skin);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        Table caixa = new Table();
        caixa.setBackground(UiSkin.retangulo(new Color(0.08f, 0.08f, 0.08f, 1f), new Color(0.2f, 0.2f, 0.2f, 1f), 1));
        caixa.top();
        caixa.add(scroll).grow().pad(4);

        TextButton.TextButtonStyle estiloRemover = new TextButton.TextButtonStyle(skin.get("vermelho", TextButton.TextButtonStyle.class));
        estiloRemover.font = skin.getFont("botao-texto-font");
        TextButton.TextButtonStyle estiloAdicionar = new TextButton.TextButtonStyle(skin.get("verde", TextButton.TextButtonStyle.class));
        estiloAdicionar.font = skin.getFont("botao-texto-font");
        TextButton remover = new TextButton("Remove", estiloRemover);
        TextButton adicionar = new TextButton("Add Friend", estiloAdicionar);
        remover.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                // Clicar na linha agora abre a janela do player; pra remover,
                // aperta Remove e depois clica no amigo.
                modoRemover = !modoRemover;
                statusAmigos.setText(modoRemover ? "Click a friend to remove." : "");
            }
        });
        adicionar.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                mostrarCampoAdicionar(true);
            }
        });
        statusAmigos = new LabelPixel("", skin.get("hud", Label.LabelStyle.class));
        escalarFonte(statusAmigos, 0.6f);
        rodapeAmigos.add(statusAmigos).expandX().left();
        rodapeAmigos.add(remover).width(110).height(44).padRight(6);
        rodapeAmigos.add(adicionar).width(130).height(44);

        // "Add Friend": campo com o nome + Add/Cancel no lugar do rodape.
        campoNovoAmigo = new com.badlogic.gdx.scenes.scene2d.ui.TextField("", skin);
        campoNovoAmigo.setMessageText("Friend name");
        campoNovoAmigo.setMaxLength(12);
        TextButton confirmar = new TextButton("Add", estiloAdicionar);
        TextButton cancelar = new TextButton("Cancel", estiloRemover);
        confirmar.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                String nome = campoNovoAmigo.getText().trim();
                if (nome.isEmpty() || !socket.isConnected()) return;
                socket.emitRaw("add_friend_by_name", GameSocket.obj(w -> w.set("friend_name", nome)));
                mostrarCampoAdicionar(false);
            }
        });
        cancelar.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                mostrarCampoAdicionar(false);
            }
        });
        rodapeAdicionar.add(campoNovoAmigo).growX().height(44).padRight(6);
        rodapeAdicionar.add(cancelar).width(100).height(44).padRight(6);
        rodapeAdicionar.add(confirmar).width(90).height(44);

        Stack rodape = new Stack(rodapeAmigos, rodapeAdicionar);
        rodapeAdicionar.setVisible(false);
        friendsPage.add(caixa).grow().row();
        friendsPage.add(rodape).growX().height(44).padTop(8);
        reconstruirListaAmigos();
    }

    private void mostrarCampoAdicionar(boolean mostrar) {
        rodapeAmigos.setVisible(!mostrar);
        rodapeAdicionar.setVisible(mostrar);
        campoNovoAmigo.setText("");
        if (mostrar && root.getStage() != null) root.getStage().setKeyboardFocus(campoNovoAmigo);
        else if (root.getStage() != null) root.getStage().setKeyboardFocus(null);
    }

    private void reconstruirListaAmigos() {
        listaAmigos.clearChildren();
        List<AmigoInfo> ordenados = new ArrayList<>(amigos.values());
        // Online primeiro, depois ordem alfabetica (sem diferenciar maiuscula).
        ordenados.sort((a, b) -> a.online != b.online ? (a.online ? -1 : 1) : a.nome.compareToIgnoreCase(b.nome));
        for (AmigoInfo a : ordenados) listaAmigos.add(linhaAmigo(a)).growX().height(40).padBottom(3).row();
        if (ordenados.isEmpty()) {
            Label vazio = new LabelPixel("No friends yet.", skin.get("hud", Label.LabelStyle.class));
            escalarFonte(vazio, 0.6f);
            listaAmigos.add(vazio).pad(10);
        }
    }

    private Table linhaAmigo(AmigoInfo a) {
        Table linha = new Table();
        linha.setBackground(UiSkin.retangulo(new Color(0.17f, 0.17f, 0.17f, 1f), new Color(0.12f, 0.12f, 0.12f, 1f), 1));
        linha.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled);
        linha.add(iconeAtlas(a.online ? "ui/OnlineIcon" : "ui/OfflineIcon", 26f)).size(26f).padLeft(8).padRight(8);
        Label nome = new LabelPixel(a.nome, skin.get("hud", Label.LabelStyle.class));
        escalarFonte(nome, 0.75f);
        linha.add(nome).expandX().left();
        if (a.seller) linha.add(iconeAtlas("ui/SellerIcon", 26f)).size(26f).padRight(4);
        if (a.guild) linha.add(iconeAtlas("ui/GuildIcon", 26f)).size(26f).padRight(4);
        if (a.pk) linha.add(iconeAtlas("ui/PkIcon", 26f)).size(26f).padRight(4);
        linha.add(iconeAtlas(iconeClasse(a.classe), 26f)).size(26f).padRight(8);
        linha.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            @Override public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                if (modoRemover) {
                    modoRemover = false;
                    statusAmigos.setText("");
                    if (socket.isConnected()) socket.emitRaw("toggle_friend", GameSocket.obj(w -> w.set("friend_name", a.nome)));
                    return;
                }
                if (aoClicarAmigo != null) aoClicarAmigo.accept(a.nome);
            }
        });
        return linha;
    }

    private Image iconeAtlas(String regiao, float tamanho) {
        TextureAtlas.AtlasRegion r = atlas.findRegion(regiao);
        Image img = r != null ? new Image(new TextureRegionDrawable(r)) : new Image();
        img.setScaling(Scaling.fit);
        img.setSize(tamanho, tamanho);
        return img;
    }

    public static String iconeClasse(String classe) {
        if (classe == null) return "ui/KnightIcon";
        switch (classe) {
            case "Bard": return "ui/BardIcon";
            case "Ranger": return "ui/RangerIcon";
            case "Mage": return "ui/MageIcon";
            default: return "ui/KnightIcon";
        }
    }

    /** friends_list do servidor: troca a lista toda. */
    public void atualizarAmigos(JsonValue dados) {
        amigos.clear();
        JsonValue lista = dados != null ? dados.get("friends") : null;
        if (lista != null) {
            for (JsonValue f = lista.child; f != null; f = f.next) {
                AmigoInfo a = new AmigoInfo(f.getString("name", ""));
                if (a.nome.isEmpty()) continue;
                a.classe = f.getString("class_name", "Knight");
                a.online = f.getBoolean("online", false);
                a.pk = f.getBoolean("icon_pk", false);
                a.guild = f.getBoolean("icon_guild", false);
                a.seller = f.getBoolean("icon_seller", false);
                a.skins = f.get("skins");
                amigos.put(a.nome, a);
            }
        }
        if (amigoSelecionado != null && !amigos.containsKey(amigoSelecionado)) amigoSelecionado = null;
        reconstruirListaAmigos();
        if (aoMudarAmigos != null) aoMudarAmigos.run();
    }

    /** friend_status: virou amigo (pede a lista de novo) ou deixou de ser. */
    public void definirAmigo(String nome, boolean amigo) {
        if (amigo) {
            if (socket.isConnected()) socket.emitRaw("get_friends_list", "{}");
            if (!amigos.containsKey(nome)) amigos.put(nome, new AmigoInfo(nome));
        } else {
            amigos.remove(nome);
            if (nome.equals(amigoSelecionado)) amigoSelecionado = null;
        }
        reconstruirListaAmigos();
        if (aoMudarAmigos != null) aoMudarAmigos.run();
    }

    /** friend_icon_updated (icon = "pk"/"guild"/"seller"). */
    public void definirIconeAmigo(String nome, String icone, boolean valor) {
        AmigoInfo a = amigos.get(nome);
        if (a == null) return;
        if ("pk".equals(icone)) a.pk = valor;
        else if ("guild".equals(icone)) a.guild = valor;
        else if ("seller".equals(icone)) a.seller = valor;
        reconstruirListaAmigos();
        if (aoMudarAmigos != null) aoMudarAmigos.run();
    }

    /** player_joined/player_left: bolinha verde/cinza sem pedir a lista toda. */
    public void definirOnline(String nome, boolean online) {
        AmigoInfo a = amigos.get(nome);
        if (a == null || a.online == online) return;
        a.online = online;
        reconstruirListaAmigos();
    }

    /** friend_add_result: mensagem curta no rodape. */
    public void resultadoAdicionarAmigo(boolean sucesso, String nome, String motivo) {
        if (sucesso) statusAmigos.setText(nome + " added.");
        else if ("self".equals(motivo)) statusAmigos.setText("You can't add yourself.");
        else statusAmigos.setText("Player not found.");
    }

    public AmigoInfo amigo(String nome) { return amigos.get(nome); }

    /** Classe/skin de quem apareceu no Rank (pra janela do player). */
    private final Map<String, AmigoInfo> infoRanking = new HashMap<>();
    public AmigoInfo infoRanking(String nome) { return infoRanking.get(nome); }

    public void definirAoMudarAmigos(Runnable r) { aoMudarAmigos = r; }

    public void definirAoClicarAmigo(java.util.function.Consumer<String> c) { aoClicarAmigo = c; }

    private Button criarBotaoAcao(String regiao, Color fundo, Color borda, Runnable acao) {
        Button.ButtonStyle estilo = new Button.ButtonStyle();
        estilo.up = UiSkin.retangulo(fundo, borda, 2);
        estilo.over = UiSkin.retangulo(fundo.cpy().mul(1.5f, 1.5f, 1.5f, 1f), borda.cpy().mul(1.4f, 1.4f, 1.4f, 1f), 2);
        estilo.down = UiSkin.retangulo(fundo.cpy().mul(2f, 2f, 2f, 1f), borda.cpy().mul(1.8f, 1.8f, 1.8f, 1f), 2);
        estilo.disabled = UiSkin.retangulo(new Color(0.1f, 0.1f, 0.1f, 1f), new Color(0.2f, 0.2f, 0.2f, 1f), 2);
        Button botao = new Button(estilo);
        Image icone = new Image(new TextureRegionDrawable(atlas.findRegion(regiao)));
        icone.setScaling(Scaling.fit);
        botao.add(icone).size(TAM_BOTAO_ACAO - 16f);
        botao.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                if (!botao.isDisabled()) acao.run();
            }
        });
        return botao;
    }

    private void alternarFavorito() {
        InventoryItem item = itemSelecionado();
        if (item == null || item.instanceId.isEmpty() || !socket.isConnected()) return;
        String payload = GameSocket.obj(w -> w.set("instance_id", item.instanceId));
        socket.emitRaw("toggle_favorite_item", payload);
    }

    private void iniciarExclusao() {
        if (modoExclusao) {
            cancelarExclusao();
            return;
        }
        modoExclusao = true;
        idsParaExcluir.clear();
        selectedItem = -1;
        selectedInstanceId = "";
        bagDetalhes.clearChildren();
        actionButtons.setVisible(false);
        deleteConfirmation.setVisible(true);
        atualizarStatusExclusao();
        atualizarGrade();
    }

    private void alternarMarcaExclusao(int indice) {
        if (indice < 0 || indice >= inventoryItems.size()) return;
        InventoryItem item = inventoryItems.get(indice);
        // Favoritos nao podem ser apagados (o servidor tambem recusa).
        if (item.favorite || item.instanceId.isEmpty()) return;
        if (!idsParaExcluir.remove(item.instanceId)) idsParaExcluir.add(item.instanceId);
        atualizarStatusExclusao();
        atualizarGrade();
    }

    private void atualizarStatusExclusao() {
        if (celulaStatus == null) return;
        if (modoExclusao) {
            int n = idsParaExcluir.size();
            actionStatus.setText(n + (n == 1 ? " Item Selected" : " Items Selected"));
            celulaStatus.height(actionStatus.getPrefHeight()).padBottom(5f);
        } else {
            actionStatus.setText("");
            celulaStatus.height(0f).padBottom(0f);
        }
        actionStatus.invalidateHierarchy();
    }

    private void confirmarExclusao() {
        if (!modoExclusao) return;
        if (!idsParaExcluir.isEmpty() && socket.isConnected()) {
            List<String> ids = new ArrayList<>(idsParaExcluir);
            String payload = GameSocket.obj(w -> {
                w.array("instance_ids");
                for (String id : ids) w.value(id);
                w.pop();
            });
            socket.emitRaw("delete_items", payload);
        }
        cancelarExclusao();
    }

    private void cancelarExclusao() {
        boolean estava = modoExclusao;
        modoExclusao = false;
        idsParaExcluir.clear();
        if (actionButtons != null) actionButtons.setVisible(true);
        if (deleteConfirmation != null) deleteConfirmation.setVisible(false);
        atualizarStatusExclusao();
        if (estava) {
            atualizarGrade();
            atualizarDetalhes(itemSelecionado());
        }
    }

    private InventoryItem itemSelecionado() {
        return selectedItem >= 0 && selectedItem < inventoryItems.size() ? inventoryItems.get(selectedItem) : null;
    }

    public String nomeExibicao(String caminho) {
        if (caminho == null || caminho.isEmpty()) return "Unknown item";
        EquipStats dados = ITEM_STATS.get(caminho);
        if (dados != null && !dados.nome.isEmpty()) return dados.nome;
        String nome = caminho.substring(caminho.lastIndexOf('/') + 1);
        int extensao = nome.lastIndexOf('.');
        if (extensao >= 0) nome = nome.substring(0, extensao);
        nome = nome.replace("Starter", "").replaceAll("([a-z])([A-Z])", "$1 $2");
        return nome.isEmpty() ? "Item" : nome;
    }

    public void atualizarInventario(JsonValue dados) {
        inventoryItems.clear();
        java.util.Set<String> idsAtuais = new java.util.HashSet<>();
        if (dados != null && dados.isArray()) {
            for (JsonValue entrada = dados.child; entrada != null; entrada = entrada.next) {
                if (!entrada.isObject()) continue;
                String caminho = entrada.getString("item", "");
                if (caminho.isEmpty()) continue;
                inventoryItems.add(new InventoryItem(entrada.getString("id", ""), caminho,
                    Math.max(1, entrada.getInt("qty", 1)), entrada.getBoolean("favorite", false)));
                idsAtuais.add(entrada.getString("id", ""));
            }
        }
        idsParaExcluir.retainAll(idsAtuais);
        atualizarStatusExclusao();
        selectedItem = -1;
        if (!selectedInstanceId.isEmpty()) {
            for (int i = 0; i < inventoryItems.size(); i++) {
                if (selectedInstanceId.equals(inventoryItems.get(i).instanceId)) {
                    selectedItem = i;
                    break;
                }
            }
            if (selectedItem < 0) selectedInstanceId = "";
        }
        atualizarDetalhes(selectedItem >= 0 ? inventoryItems.get(selectedItem) : null);
        atualizarGrade();
        selectedEquipCandidate = -1;
        atualizarGradeEquip();
        atualizarDetalhesEquip();
        aoMudarBagOuAtalhos();
        atualizarTelaBanco(); atualizarTelaLoja();
    }

    public void adicionarItens(JsonValue dados) {
        if (dados == null || !dados.isArray()) return;
        for (JsonValue entrada = dados.child; entrada != null; entrada = entrada.next) {
            if (!entrada.isObject()) continue;
            String caminho = entrada.getString("item", "");
            if (!caminho.isEmpty()) inventoryItems.add(new InventoryItem(entrada.getString("id", ""), caminho,
                Math.max(1, entrada.getInt("qty", 1)), entrada.getBoolean("favorite", false)));
        }
        atualizarGrade();
        atualizarGradeEquip();
        aoMudarBagOuAtalhos();
        atualizarTelaBanco(); atualizarTelaLoja();
    }

    // ===================== SPELLS (atalhos) =====================
    // 9 slots livres (teclas 1-9): cada um recebe um item usavel (comida;
    // pocao/magia quando existirem). Clicar num slot abre a lista do que pode
    // ir nele; escolher manda pro servidor (set_hotbar), que salva e devolve
    // (hotbar_synced). A barra da gameplay e' a HotbarUI.

    public static final int SLOTS_ATALHO = 9;
    private final String[] atalhos = new String[SLOTS_ATALHO];
    { java.util.Arrays.fill(atalhos, ""); }
    private int slotAtalhoEscolhendo = -1; // slot com a lista aberta
    private Runnable aoMudarAtalhos;

    public void definirAoMudarAtalhos(Runnable r) { aoMudarAtalhos = r; }
    public String[] atalhos() { return atalhos; }

    /** Quantidade total de um item na bag (somando as pilhas). */
    public int quantidadeNaBag(String caminho) {
        int total = 0;
        for (InventoryItem it : inventoryItems) if (it.itemPath.equals(caminho)) total += it.quantity;
        return total;
    }

    /** {"slots": [8]} do servidor (sync_local_player / hotbar_synced). */
    public void definirHotbar(JsonValue hb) {
        if (hb == null || !hb.isObject()) return;
        JsonValue lista = hb.get("slots");
        for (int i = 0; i < atalhos.length; i++) {
            JsonValue v = lista != null && lista.isArray() ? lista.get(i) : null;
            atalhos[i] = v != null && v.isString() ? v.asString() : "";
        }
        aoMudarBagOuAtalhos();
    }

    private void aoMudarBagOuAtalhos() {
        if ("Spells".equals(secaoAtual)) construirPaginaSpells();
        if (aoMudarAtalhos != null) aoMudarAtalhos.run();
    }

    /** Item que pode ir nesse slot (igual no PC e no celular). */
    private boolean podeIrNoSlot(String caminho, int slot) {
        return usavelNoAtalho(caminho);
    }

    private final java.util.Set<String> magiasConhecidas = new java.util.LinkedHashSet<>();

    /** Magia (Igniculus...) - vai na hotbar sem estar na bag. */
    public boolean ehMagia(String caminho) {
        EquipStats dados = caminho == null ? null : ITEM_STATS.get(caminho);
        return dados != null && dados.magia != null && !dados.magia.isEmpty();
    }

    public boolean conheceMagia(String caminho) { return magiasConhecidas.contains(caminho); }

    /** Mana gasta pela magia (0 = nao e' magia). */
    public int manaDaMagia(String caminho) {
        EquipStats dados = caminho == null ? null : ITEM_STATS.get(caminho);
        return dados == null ? 0 : dados.magiaMana;
    }

    /** "(icone de MP) 50" em azul, pro canto do slot de uma magia. */
    public Table rotuloManaMagia(String caminho, float tamIcone) { return rotuloManaMagia(caminho, tamIcone, "quantidade"); }

    public Table rotuloManaMagia(String caminho, float tamIcone, String estilo) {
        Table t = new Table();
        TextureAtlas.AtlasRegion r = atlas.findRegion("ui/MPIcon");
        if (r != null) {
            Image img = new Image(new TextureRegionDrawable(r));
            img.setScaling(Scaling.fit);
            t.add(img).size(tamIcone).padRight(1);
        }
        Label valor = new LabelPixel(String.valueOf(manaDaMagia(caminho)), skin.get(estilo, Label.LabelStyle.class));
        valor.setColor(COR_MANA);
        t.add(valor);
        return t;
    }

    /** Slot da hotbar com algo usavel agora: magia aprendida ou item na bag. */
    public boolean disponivelNoAtalho(String caminho) {
        return ehMagia(caminho) ? conheceMagia(caminho) : quantidadeNaBag(caminho) > 0;
    }

    /** Pocao (cura HP/MP): tem o cooldown compartilhado (CooldownPocao). */
    public boolean ehPocao(String caminho) {
        EquipStats dados = caminho == null ? null : ITEM_STATS.get(caminho);
        return dados != null && dados.ehPocao();
    }

    /** Item com cooldown: pocao (todas juntas), scroll (os scrolls juntos) ou magia (cada uma o seu). */
    public boolean temCooldown(String caminho) {
        EquipStats dados = caminho == null ? null : ITEM_STATS.get(caminho);
        return dados != null && (dados.ehPocao() || dados.usavel || ehMagia(caminho));
    }

    /** Cinza escuro + segundos por cima do slot de uma pocao/scroll em cooldown. */
    public CooldownPocao.Sobreposicao sobreposicaoCooldown(String caminho) {
        EquipStats dados = caminho == null ? null : ITEM_STATS.get(caminho);
        if (ehMagia(caminho)) return new CooldownPocao.Sobreposicao(skin.getFont("cooldown-font"), caminho);
        boolean scroll = dados != null && dados.usavel && !dados.ehPocao();
        return new CooldownPocao.Sobreposicao(skin.getFont("cooldown-font"), scroll);
    }

    /** Item que pode ir num slot da barra: comida e pocao. */
    private boolean usavelNoAtalho(String caminho) {
        EquipStats dados = ITEM_STATS.get(caminho);
        return dados != null && (dados.fullness > 0 || dados.ehPocao() || dados.usavel || ehMagia(caminho));
    }

    /** Aba da direita na pagina Spells: itens da bag ou magias. */
    private boolean escolhendoMagias = false;

    private static boolean ehMobile() {
        return Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android
            || Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.iOS;
    }

    /** Quantos slots aparecem: PC os 9; celular, um por botao na tela. */
    private int slotsVisiveis() {
        return ehMobile() ? Math.min(SLOTS_ATALHO, Controles.layoutMobile().size()) : SLOTS_ATALHO;
    }

    /** Painel escuro com borda, igual a coluna de detalhes da Bag. */
    private Table painelSpells() {
        Table t = new Table();
        t.setBackground(UiSkin.retangulo(new Color(0.08f, 0.08f, 0.08f, 1f), new Color(0.35f, 0.35f, 0.35f, 1f), 1));
        t.top().left();
        t.pad(10);
        return t;
    }

    private TextButton botaoSpells(String texto, String estiloBase, Runnable acao) {
        TextButton.TextButtonStyle e = new TextButton.TextButtonStyle(skin.get(estiloBase, TextButton.TextButtonStyle.class));
        e.font = skin.getFont("botao-texto-font");
        TextButton b = new TextButton(texto, e);
        b.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) { acao.run(); }
        });
        return b;
    }

    // Esquerda: os slots da hotbar em grade 3x3 (celular: so' os que tem botao
    // na tela). Clicar seleciona; a direita mostra Inventory/Spells e clicar
    // num item coloca ele no slot.
    // ===================== MERCADO (Luna) =====================
    // Lista de todos os itens (busca + filtros); clicar num item mostra as
    // ofertas de compra/venda dele e o formulario pra criar a sua. "My offers"
    // lista as suas pra cancelar. Servidor: servidor.py::handle_market_*.
    private final Table mercadoPage = new Table();
    private final Table mercadoLista = new Table();
    private final Table mercadoDetalhe = new Table();
    private boolean modoMercado = false, mercadoAbaVenda = true, mercadoVendoMinhas = false;
    private String mercadoNpc = "", mercadoItem = null, secaoAntesDoMercado = "Bag";
    private JsonValue mercadoOfertas, mercadoMinhas;
    private int mercadoTenho = 0, mercadoTaxa = 10;
    private String mercadoAviso = "";
    private com.badlogic.gdx.scenes.scene2d.ui.TextField mercadoBusca, mercadoQtd, mercadoPreco;
    private com.badlogic.gdx.scenes.scene2d.ui.SelectBox<String> mercadoClasse, mercadoNivel, mercadoTipo;
    private static final String[] FILTRO_CLASSES = {"All classes", "Knight", "Ranger", "Mage", "Bard", "Universal"};
    private static final String[] FILTRO_NIVEIS = {"All levels", "Lv 0-9", "Lv 10-19", "Lv 20-29", "Lv 30+"};
    private static final String[] FILTRO_TIPOS = {"All types", "Weapons", "Off-hand", "Armor", "Ammo", "Potions", "Food", "Scrolls", "Other"};

    public boolean emMercado() { return modoMercado; }

    public void abrirMercado(String npcId, int taxa) {
        if (modoTrade) return;
        if (modoBanco) fecharBanco();
        if (modoLoja) fecharLoja();
        if (mercadoPage.getChildren().size == 0) construirPaginaMercado();
        mercadoNpc = npcId == null ? "" : npcId;
        mercadoTaxa = taxa;
        mercadoItem = null; mercadoOfertas = null; mercadoVendoMinhas = false; mercadoAviso = "";
        if (!modoMercado) secaoAntesDoMercado = secaoAtual;
        modoMercado = true;
        cancelarExclusao();
        if (colunaEsquerdaAbas != null) colunaEsquerdaAbas.setVisible(false);
        if (colunaDireitaAbas != null) colunaDireitaAbas.setVisible(false);
        mainWindow.clearChildren();
        mainWindow.pad(8);
        mainWindow.add(mercadoPage).grow();
        actionBar.setVisible(false);
        atualizarListaMercado();
        atualizarDetalheMercado();
        root.setVisible(true);
    }

    public void fecharMercado() {
        fecharPopupQuantidade();
        if (!modoMercado) return;
        modoMercado = false;
        if (colunaEsquerdaAbas != null) colunaEsquerdaAbas.setVisible(true);
        if (colunaDireitaAbas != null) colunaDireitaAbas.setVisible(true);
        root.setVisible(false);
        selecionarSecao(secaoAntesDoMercado);
    }

    /** market_offers: {item, buy:[{id,qty,price,owner,mine}], sell:[...], have}. */
    public void mercadoRecebeuOfertas(JsonValue d) {
        if (!modoMercado || d == null || !d.getString("item", "").equals(mercadoItem)) return;
        mercadoOfertas = d;
        mercadoTenho = d.getInt("have", 0);
        atualizarDetalheMercado();
    }

    /** market_mine: {offers:[{id,kind,item,qty,price}]}. */
    public void mercadoRecebeuMinhas(JsonValue d) {
        mercadoMinhas = d;
        if (modoMercado && mercadoVendoMinhas) atualizarDetalheMercado();
    }

    /** market_result: {ok, msg, currency}. */
    public void mercadoResultado(JsonValue d) {
        if (d == null) return;
        atualizarMoedas(d.getLong("currency", currencyTotal));
        mercadoAviso = d.getString("msg", "");
        if (modoMercado) atualizarDetalheMercado();
    }

    /** Manda um evento do mercado com o npc_id junto (campos: os outros). */
    private void emitirMercado(String evento, GameSocket.PayloadWriter campos) {
        if (!socket.isConnected()) return;
        String npc = mercadoNpc;
        socket.emitRaw(evento, GameSocket.obj(w -> {
            w.set("npc_id", npc);
            campos.write(w);
        }));
    }

    private com.badlogic.gdx.scenes.scene2d.ui.SelectBox<String> seletorMercado(String[] opcoes) {
        com.badlogic.gdx.scenes.scene2d.ui.SelectBox<String> sb = new com.badlogic.gdx.scenes.scene2d.ui.SelectBox<>(skin, "zoom-select");
        sb.setItems(opcoes);
        sb.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) { atualizarListaMercado(); }
        });
        return sb;
    }

    private void construirPaginaMercado() {
        mercadoPage.top().left();
        mercadoBusca = new com.badlogic.gdx.scenes.scene2d.ui.TextField("", skin);
        mercadoBusca.setMessageText("Search item...");
        mercadoBusca.setTextFieldListener((campo, c) -> atualizarListaMercado());
        TextButton minhas = botaoSpells("My offers", "default", () -> {
            mercadoVendoMinhas = !mercadoVendoMinhas;
            mercadoAviso = "";
            atualizarDetalheMercado();
        });
        TextButton fechar = new TextButton("X", estiloPlano(COR_BLOQUEADO, Color.WHITE));
        fechar.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) { setVisible(false); }
        });
        Table topo = new Table();
        topo.add(mercadoBusca).growX().height(38).padRight(6);
        topo.add(minhas).width(130).height(38).padRight(6);
        topo.add(fechar).size(38);
        mercadoClasse = seletorMercado(FILTRO_CLASSES);
        mercadoNivel = seletorMercado(FILTRO_NIVEIS);
        mercadoTipo = seletorMercado(FILTRO_TIPOS);
        Table filtros = new Table();
        filtros.add(mercadoClasse).growX().height(32).padRight(4);
        filtros.add(mercadoNivel).growX().height(32).padRight(4);
        filtros.add(mercadoTipo).growX().height(32);
        mercadoLista.top().left();
        ScrollPane rolagemLista = rolagem(mercadoLista);
        rolagemComRodinha(rolagemLista);
        Table esquerda = painelSpells();
        esquerda.add(rolagemLista).grow();
        Table direita = painelSpells();
        mercadoDetalhe.top().left();
        direita.add(mercadoDetalhe).grow();
        Table corpo = new Table();
        corpo.add(esquerda).width(250).growY().padRight(6);
        corpo.add(direita).grow();
        mercadoPage.add(topo).growX().padBottom(6).row();
        mercadoPage.add(filtros).growX().padBottom(6).row();
        mercadoPage.add(corpo).grow();
    }

    /** O item passa nos filtros e na busca? */
    private boolean passaFiltrosMercado(String caminho, EquipStats d) {
        String busca = mercadoBusca.getText().trim().toLowerCase();
        if (!busca.isEmpty() && !nomeExibicao(caminho).toLowerCase().contains(busca)) return false;
        int c = mercadoClasse.getSelectedIndex();
        if (c >= 1 && c <= 4 && !("All".equals(d.reqClass) || FILTRO_CLASSES[c].equals(d.reqClass))) return false;
        if (c == 5 && !"All".equals(d.reqClass)) return false;
        int n = mercadoNivel.getSelectedIndex();
        if (n >= 1) {
            int min = (n - 1) * 10, max = n == 4 ? Integer.MAX_VALUE : min + 9;
            if (d.reqLevel < min || d.reqLevel > max) return false;
        }
        String slot = slotDoItem(caminho);
        String tipo;
        if ("MainHand".equals(slot)) tipo = "Weapons";
        else if ("Hand".equals(slot)) tipo = d.empilhavel ? "Ammo" : "Off-hand";
        else if (slot != null) tipo = "Armor";
        else if (d.ehPocao()) tipo = "Potions";
        else if (d.fullness > 0) tipo = "Food";
        else if (d.usavel) tipo = "Scrolls";
        else tipo = "Other";
        int t = mercadoTipo.getSelectedIndex();
        return t == 0 || FILTRO_TIPOS[t].equals(tipo);
    }

    private void atualizarListaMercado() {
        if (mercadoBusca == null) return;
        mercadoLista.clearChildren();
        List<String> itens = new ArrayList<>();
        java.util.Set<String> gruposVistos = new java.util.HashSet<>();
        for (Map.Entry<String, EquipStats> e : ITEM_STATS.entrySet()) {
            if (ehMagia(e.getKey()) || e.getValue().nome.isEmpty()) continue;
            if (!passaFiltrosMercado(e.getKey(), e.getValue())) continue;
            // Anel/colar com ADC sorteado: uma linha so' (a variacao da minha classe).
            List<String> variantes = variantesMercado(e.getKey());
            if (variantes.size() > 1) {
                if (!gruposVistos.add(variantes.get(0))) continue;
                itens.add(varianteDaClasse(variantes, classeJogador));
            } else itens.add(e.getKey());
        }
        itens.sort((a, b) -> nomeExibicao(a).compareToIgnoreCase(nomeExibicao(b)));
        for (String caminho : itens) mercadoLista.add(gavetaItemMercado(caminho)).growX().padBottom(3).row();
        if (itens.isEmpty()) mercadoLista.add(linhaStat("No items found.", Color.GRAY)).left().pad(4);
    }

    /** Gaveta da lista: icone + nome (+ level); clicar abre as ofertas dele. */
    private Button gavetaItemMercado(String caminho) {
        boolean escolhido = variantesMercado(caminho).contains(mercadoItem);
        Button.ButtonStyle estilo = new Button.ButtonStyle();
        estilo.up = UiSkin.retangulo(new Color(0.11f, 0.11f, 0.11f, 1f), escolhido ? Color.valueOf("ffd56b") : new Color(0.25f, 0.25f, 0.25f, 1f), 1);
        estilo.over = UiSkin.retangulo(new Color(0.16f, 0.16f, 0.16f, 1f), escolhido ? Color.valueOf("ffd56b") : new Color(0.4f, 0.4f, 0.4f, 1f), 1);
        Button g = new Button(estilo);
        g.left().pad(3, 4, 3, 6);
        TextureAtlas.AtlasRegion icone = iconeDoItem(caminho);
        if (icone != null) {
            Image img = new Image(new TextureRegionDrawable(icone));
            img.setScaling(Scaling.fit);
            g.add(img).size(30).padRight(6);
        }
        Table txt = new Table();
        txt.left();
        txt.add(linhaStat(nomeExibicao(caminho), Color.WHITE)).left().row();
        EquipStats d = ITEM_STATS.get(caminho);
        if (d != null && d.reqLevel > 0) txt.add(linhaStat("Lv " + d.reqLevel, COR_REQUISITO)).left().row();
        g.add(txt).growX().left();
        g.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                mercadoItem = caminho;
                mercadoOfertas = null;
                mercadoVendoMinhas = false;
                mercadoAviso = "";
                atualizarListaMercado();
                atualizarDetalheMercado();
                emitirMercado("market_item", w -> w.set("item", caminho));
            }
        });
        return g;
    }

    /** Variacoes do mesmo item universal que so' mudam o ADC (Ruby Ring de
     * Melee/Magic/Focus/Musicality): mesmo nome e tipo. Item comum: so' ele. */
    private List<String> variantesMercado(String caminho) {
        List<String> out = new ArrayList<>();
        EquipStats d = caminho == null ? null : ITEM_STATS.get(caminho);
        if (d == null || !"All".equals(d.reqClass) || adcDeClasse(d.fourthStatType) == null) {
            if (caminho != null) out.add(caminho);
            return out;
        }
        for (Map.Entry<String, EquipStats> e : ITEM_STATS.entrySet()) {
            EquipStats o = e.getValue();
            if (o.nome.equals(d.nome) && o.tipo.equals(d.tipo) && "All".equals(o.reqClass)
                    && adcDeClasse(o.fourthStatType) != null) out.add(e.getKey());
        }
        out.sort(String::compareTo);
        return out;
    }

    /** ADC que alimenta a classe (servidor.py::ADC_DA_CLASSE, o principal). */
    private static String adcDaClasse(String classe) {
        switch (classe == null ? "" : classe) {
            case "Mage": return "Magic";
            case "Ranger": return "Focus";
            case "Bard": return "Musicality";
            default: return "Melee";
        }
    }

    /** Classe dona do ADC (null = nao e' ADC de classe). */
    private static String adcDeClasse(String adc) {
        if (adc == null) return null;
        switch (adc) {
            case "Melee": return "Knight";
            case "Magic": return "Mage";
            case "Focus": return "Ranger";
            case "Musicality": return "Bard";
            default: return null;
        }
    }

    private String varianteDaClasse(List<String> variantes, String classe) {
        String adc = adcDaClasse(classe);
        for (String v : variantes) {
            EquipStats d = ITEM_STATS.get(v);
            if (d != null && adc.equals(d.fourthStatType)) return v;
        }
        return variantes.isEmpty() ? null : variantes.get(0);
    }

    /** "1g 50s" (igual servidor.py::formatar_moedas). */
    private static String formatarMoedas(long v) {
        StringBuilder sb = new StringBuilder();
        String[] nomes = {"p", "g", "s", "c"};
        long[] valores = {1000000L, 10000L, 100L, 1L};
        for (int i = 0; i < 4; i++) {
            if (v >= valores[i] || (i == 3 && sb.length() == 0)) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(v / valores[i]).append(nomes[i]);
                v %= valores[i];
            }
        }
        return sb.toString();
    }

    /** "150", "5s", "2g 50s" -> copper; -1 = invalido. */
    private static long lerPreco(String texto) {
        if (texto == null) return -1;
        String t = texto.trim().toLowerCase().replace(",", "");
        if (t.isEmpty()) return -1;
        long total = 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)\\s*([cpsg]?)").matcher(t);
        int fim = 0;
        while (m.find()) {
            if (!t.substring(fim, m.start()).trim().isEmpty()) return -1;
            long n = Long.parseLong(m.group(1));
            switch (m.group(2)) {
                case "s": n *= 100L; break;
                case "g": n *= 10000L; break;
                case "p": n *= 1000000L; break;
                default: break;
            }
            total += n;
            fim = m.end();
        }
        return t.substring(fim).trim().isEmpty() && total > 0 ? total : -1;
    }

    private static long lerInteiro(String texto) {
        try { return Long.parseLong(texto.trim()); } catch (RuntimeException e) { return -1; }
    }

    private void atualizarDetalheMercado() {
        mercadoDetalhe.clearChildren();
        mercadoDetalhe.top().left();
        if (mercadoVendoMinhas) { detalheMinhasOfertas(); return; }
        if (mercadoItem == null) {
            mercadoDetalhe.add(linhaStat("Select an item on the left.", Color.GRAY)).expand().center();
            return;
        }
        String caminho = mercadoItem;
        // Cabecalho: icone + nome + quantos tenho na bag.
        Table cab = new Table();
        cab.left();
        TextureAtlas.AtlasRegion icone = iconeDoItem(caminho);
        if (icone != null) {
            Image img = new Image(new TextureRegionDrawable(icone));
            img.setScaling(Scaling.fit);
            cab.add(img).size(36).padRight(8);
        }
        Table nomes = new Table();
        nomes.left();
        nomes.add(new LabelPixel(nomeExibicao(caminho), new Label.LabelStyle(UiSkin.fonteDescricao(skin, 15), Color.WHITE))).left().row();
        nomes.add(linhaStat("In your bag: " + mercadoTenho, Color.LIGHT_GRAY)).left().row();
        cab.add(nomes).left();
        mercadoDetalhe.add(cab).left().padBottom(6).row();
        // Item universal com ADC de cada classe (Ruby Ring...): escolhe qual.
        List<String> variantes = variantesMercado(caminho);
        if (variantes.size() > 1) {
            Table filtro = new Table();
            filtro.left();
            for (String classe : new String[]{"Knight", "Mage", "Ranger", "Bard"}) {
                String variante = varianteDaClasse(variantes, classe);
                if (variante == null || !adcDaClasse(classe).equals(ITEM_STATS.get(variante).fourthStatType)) continue;
                filtro.add(botaoSpells(classe, variante.equals(caminho) ? "verde" : "default", () -> {
                    if (variante.equals(mercadoItem)) return;
                    mercadoItem = variante;
                    mercadoOfertas = null;
                    mercadoAviso = "";
                    atualizarListaMercado();
                    atualizarDetalheMercado();
                    emitirMercado("market_item", w -> w.set("item", variante));
                })).width(96).height(30).padRight(4);
            }
            mercadoDetalhe.add(filtro).left().padBottom(6).row();
        }
        // Abas: Sell offers (outros vendendo) / Buy offers (outros comprando).
        Table abas = new Table();
        abas.left();
        abas.add(botaoSpells("Sell", mercadoAbaVenda ? "verde" : "default", () -> {
            mercadoAbaVenda = true; mercadoAviso = ""; atualizarDetalheMercado();
        })).width(140).height(34).padRight(6);
        abas.add(botaoSpells("Buy", mercadoAbaVenda ? "default" : "verde", () -> {
            mercadoAbaVenda = false; mercadoAviso = ""; atualizarDetalheMercado();
        })).width(140).height(34);
        mercadoDetalhe.add(abas).left().padBottom(6).row();
        // Lista das ofertas.
        Table lista = new Table();
        lista.top().left();
        JsonValue ofertas = mercadoOfertas == null ? null : mercadoOfertas.get(mercadoAbaVenda ? "sell" : "buy");
        if (mercadoOfertas == null) lista.add(linhaStat("Loading...", Color.GRAY)).left();
        else if (ofertas == null || ofertas.size == 0) lista.add(linhaStat(mercadoAbaVenda ? "Nobody is selling this." : "Nobody is buying this.", Color.GRAY)).left();
        else for (JsonValue o = ofertas.child; o != null; o = o.next) lista.add(linhaOferta(caminho, o)).growX().padBottom(3).row();
        ScrollPane rolagemOfertas = rolagem(lista);
        rolagemComRodinha(rolagemOfertas);
        mercadoDetalhe.add(rolagemOfertas).grow().row();
        // Criar a minha oferta (do tipo da aba): abre uma janelinha por cima.
        mercadoDetalhe.add(botaoSpells(mercadoAbaVenda ? "Create sell offer" : "Create buy offer", "verde",
            () -> abrirPopupOferta(caminho))).width(220).height(36).left().padTop(6).row();
        if (!mercadoAviso.isEmpty()) mercadoDetalhe.add(linhaStat(mercadoAviso, Color.valueOf("ffd56b"))).left().padTop(4).row();
    }

    /** Uma oferta: quantidade, preco por unidade, dono e o botao. */
    private Table linhaOferta(String caminho, JsonValue o) {
        long id = o.getLong("id", 0), preco = o.getLong("price", 0);
        int qtd = o.getInt("qty", 0);
        boolean minha = o.getBoolean("mine", false);
        Table linha = new Table();
        linha.setBackground(UiSkin.retangulo(new Color(0.1f, 0.1f, 0.1f, 1f), new Color(0.25f, 0.25f, 0.25f, 1f), 1));
        linha.pad(3, 6, 3, 4);
        Table info = new Table();
        info.left();
        Table cima = new Table();
        cima.add(linhaStat(qtd + "x", Color.WHITE)).padRight(8);
        cima.add(valorComMoedas(preco)).padRight(4);
        cima.add(linhaStat("each", Color.GRAY));
        info.add(cima).left().row();
        Table baixo = new Table();
        baixo.add(linhaStat((minha ? "You" : o.getString("owner", "")) + "  -  total", Color.GRAY)).padRight(6);
        baixo.add(valorComMoedas(preco * qtd));
        info.add(baixo).left().row();
        linha.add(info).growX().left();
        TextButton acao;
        if (minha) {
            acao = botaoSpells("Cancel", "vermelho", () -> emitirMercado("market_cancel", w -> w.set("id", id)));
        } else if (mercadoAbaVenda) {
            // Alguem vende: eu compro (ate' o que o dinheiro paga).
            int max = (int) Math.min(qtd, preco > 0 ? currencyTotal / preco : 0);
            acao = botaoSpells(max > 0 ? "Buy" : "No money", max > 0 ? "verde" : "default", () -> {
                if (max <= 0) return;
                if (max == 1) { emitirMercado("market_accept", w -> { w.set("id", id); w.set("qty", 1); }); return; }
                abrirPopupQuantidade(nomeExibicao(caminho), iconeDoItem(caminho), max, null,
                    v -> emitirMercado("market_accept", w -> { w.set("id", id); w.set("qty", v); }));
            });
        } else {
            // Alguem compra: eu vendo (ate' o que tenho na bag).
            int max = Math.min(qtd, mercadoTenho);
            acao = botaoSpells(max > 0 ? "Sell" : "None", max > 0 ? "verde" : "default", () -> {
                if (max <= 0) return;
                if (max == 1) { emitirMercado("market_accept", w -> { w.set("id", id); w.set("qty", 1); }); return; }
                abrirPopupQuantidade(nomeExibicao(caminho), iconeDoItem(caminho), max, null,
                    v -> emitirMercado("market_accept", w -> { w.set("id", id); w.set("qty", v); }));
            });
        }
        linha.add(acao).width(110).height(34);
        return linha;
    }

    /** Valor em moedas com icone: so' as que aparecem (ex: [ouro]1 [prata]50). */
    private Table valorComMoedas(long valor) {
        Table t = new Table();
        boolean algum = false;
        for (int i = MOEDAS_TRADE.length - 1; i >= 0; i--) {
            long q = (valor / VALOR_MOEDA[i]) % (i == MOEDAS_TRADE.length - 1 ? Long.MAX_VALUE : 100L);
            if (q <= 0 && !(i == 0 && !algum)) continue;
            algum = true;
            TextureAtlas.AtlasRegion r = atlas.findRegion("ui/currency/" + MOEDAS_TRADE[i]);
            if (r != null) {
                Image img = new Image(new TextureRegionDrawable(r));
                img.setScaling(Scaling.fit);
                t.add(img).size(18).padRight(2);
            }
            Label n = new LabelPixel(String.valueOf(q), new Label.LabelStyle(UiSkin.fontePreco(skin), Color.WHITE));
            n.setColor(corMoeda(MOEDAS_TRADE[i]));
            t.add(n).padRight(6);
        }
        return t;
    }

    /** Campos do preco: um por moeda (platina, ouro, prata, cobre) com o icone. */
    private final com.badlogic.gdx.scenes.scene2d.ui.TextField[] mercadoMoedas = new com.badlogic.gdx.scenes.scene2d.ui.TextField[4];

    private long precoDosCampos() {
        long total = 0;
        for (int i = 0; i < 4; i++) {
            if (mercadoMoedas[i] == null) continue;
            String t = mercadoMoedas[i].getText().trim();
            if (t.isEmpty()) continue;
            long n = lerInteiro(t);
            if (n < 0) return -1;
            total += n * VALOR_MOEDA[i];
        }
        return total;
    }

    /** Quantidade + preco por unidade (4 moedas) + "Total: X + 10% Fee = Y" + Create. */
    private Table formularioOferta(String caminho) {
        Table f = new Table();
        f.left();
        // Titulo: icone + "Sell <item>" / "Buy <item>".
        Table titulo = new Table();
        TextureAtlas.AtlasRegion iconeTitulo = iconeDoItem(caminho);
        if (iconeTitulo != null) {
            Image img = new Image(new TextureRegionDrawable(iconeTitulo));
            img.setScaling(Scaling.fit);
            titulo.add(img).size(30).padRight(6);
        }
        titulo.add(new LabelPixel((mercadoAbaVenda ? "Sell " : "Buy ") + nomeExibicao(caminho),
            new Label.LabelStyle(UiSkin.fonteDescricao(skin, 15), Color.WHITE)));
        f.add(titulo).left().padBottom(8).row();
        // Quantidade
        String qtdAntes = mercadoQtd != null ? mercadoQtd.getText() : "1";
        mercadoQtd = new com.badlogic.gdx.scenes.scene2d.ui.TextField(qtdAntes, skin);
        mercadoQtd.setTextFieldFilter(new com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldFilter.DigitsOnlyFilter());
        Table linhaQtd = new Table();
        linhaQtd.add(linhaStat("Quantity", Color.LIGHT_GRAY)).padRight(8);
        linhaQtd.add(mercadoQtd).width(80).height(32);
        f.add(linhaQtd).left().padBottom(4).row();
        // Preco por unidade: platina, ouro, prata, cobre
        Table linhaPreco = new Table();
        linhaPreco.add(linhaStat("Price each", Color.LIGHT_GRAY)).padRight(8);
        for (int i = MOEDAS_TRADE.length - 1; i >= 0; i--) {
            String antes = mercadoMoedas[i] != null ? mercadoMoedas[i].getText() : "";
            mercadoMoedas[i] = new com.badlogic.gdx.scenes.scene2d.ui.TextField(antes, skin);
            mercadoMoedas[i].setTextFieldFilter(new com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldFilter.DigitsOnlyFilter());
            mercadoMoedas[i].setMessageText("0");
            TextureAtlas.AtlasRegion r = atlas.findRegion("ui/currency/" + MOEDAS_TRADE[i]);
            if (r != null) {
                Image img = new Image(new TextureRegionDrawable(r));
                img.setScaling(Scaling.fit);
                linhaPreco.add(img).size(20).padRight(2);
            }
            linhaPreco.add(mercadoMoedas[i]).width(58).height(32).padRight(6);
        }
        f.add(linhaPreco).left().padBottom(4).row();
        // Resumo: "Total: X + 10% Fee = Y" (venda) / "You pay: X" (compra)
        Table resumo = new Table();
        resumo.left();
        Runnable atualizarResumo = () -> {
            resumo.clearChildren();
            long q = lerInteiro(mercadoQtd.getText()), pr = precoDosCampos();
            if (q <= 0 || pr <= 0) { resumo.add(linhaStat("Type the quantity and the price.", Color.GRAY)); return; }
            long total = q * pr;
            if (mercadoAbaVenda) {
                long taxa = Math.max(1, (long) Math.ceil(total * mercadoTaxa / 100.0));
                // "Total: 100 + 10% Fee = 110" e embaixo o que sai agora (so' a taxa).
                resumo.add(linhaStat("Total:", Color.LIGHT_GRAY)).padRight(4);
                resumo.add(valorComMoedas(total));
                resumo.add(linhaStat("+ " + mercadoTaxa + "% Fee =", Color.LIGHT_GRAY)).padRight(4);
                resumo.add(valorComMoedas(total + taxa)).row();
                Table agora = new Table();
                agora.add(linhaStat("Fee paid now:", Color.GRAY)).padRight(4);
                agora.add(valorComMoedas(taxa));
                resumo.add(agora).left().colspan(5).padTop(2);
            } else {
                resumo.add(linhaStat("You pay now:", Color.LIGHT_GRAY)).padRight(4);
                resumo.add(valorComMoedas(total));
            }
        };
        com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldListener ouvinte = (c, ch) -> atualizarResumo.run();
        mercadoQtd.setTextFieldListener(ouvinte);
        for (com.badlogic.gdx.scenes.scene2d.ui.TextField campo : mercadoMoedas) campo.setTextFieldListener(ouvinte);
        atualizarResumo.run();
        f.add(resumo).left().padBottom(6).row();
        return f;
    }

    /** Janelinha por cima do mercado pra criar a oferta (nao aperta a lista). */
    private void abrirPopupOferta(String caminho) {
        fecharPopupQuantidade();
        Stage stage = root.getStage();
        if (stage == null) return;
        Table caixa = formularioOferta(caminho);
        caixa.setBackground(UiSkin.retangulo(COR_POPUP_FUNDO, COR_POPUP_BORDA, 2));
        caixa.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled);
        caixa.pad(14, 16, 14, 16);
        Label aviso = linhaStat("", Color.valueOf("ff6a6a"));
        TextButton confirmar = botaoSpells("Confirm", "verde", () -> {
            long q = lerInteiro(mercadoQtd.getText()), pr = precoDosCampos();
            if (q <= 0 || pr <= 0) { aviso.setText("Type the quantity and the price."); return; }
            String tipo = mercadoAbaVenda ? "sell" : "buy";
            emitirMercado("market_create", w -> {
                w.set("kind", tipo);
                w.set("item", caminho);
                w.set("qty", q);
                w.set("price", pr);
            });
            fecharPopupQuantidade();
        });
        TextButton cancelar = botaoSpells("Cancel", "vermelho", this::fecharPopupQuantidade);
        Table botoes = new Table();
        botoes.add(cancelar).width(130).height(38).padRight(10);
        botoes.add(confirmar).width(130).height(38);
        caixa.add(aviso).left().row();
        caixa.add(botoes).right().padTop(6);
        popupQuantidade = new Table();
        popupQuantidade.setFillParent(true);
        popupQuantidade.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled); // bloqueia o mercado atras
        popupQuantidade.setBackground(UiSkin.retangulo(new Color(0f, 0f, 0f, 0.45f), new Color(0f, 0f, 0f, 0.45f), 1));
        popupQuantidade.center();
        popupQuantidade.add(caixa);
        stage.addActor(popupQuantidade);
        stage.setKeyboardFocus(mercadoQtd);
    }

    private void detalheMinhasOfertas() {
        mercadoDetalhe.add(linhaStat("My offers", Color.WHITE)).left().padBottom(6).row();
        Table lista = new Table();
        lista.top().left();
        JsonValue ofertas = mercadoMinhas == null ? null : mercadoMinhas.get("offers");
        if (ofertas == null || ofertas.size == 0) lista.add(linhaStat("You have no offers.", Color.GRAY)).left();
        else for (JsonValue o = ofertas.child; o != null; o = o.next) {
            String caminho = o.getString("item", "");
            long id = o.getLong("id", 0), preco = o.getLong("price", 0);
            int qtd = o.getInt("qty", 0);
            boolean venda = "sell".equals(o.getString("kind", ""));
            Table linha = new Table();
            linha.setBackground(UiSkin.retangulo(new Color(0.1f, 0.1f, 0.1f, 1f), new Color(0.25f, 0.25f, 0.25f, 1f), 1));
            linha.pad(3, 6, 3, 4);
            TextureAtlas.AtlasRegion icone = iconeDoItem(caminho);
            if (icone != null) {
                Image img = new Image(new TextureRegionDrawable(icone));
                img.setScaling(Scaling.fit);
                linha.add(img).size(28).padRight(6);
            }
            Table info = new Table();
            info.left();
            info.add(linhaStat((venda ? "Selling " : "Buying ") + qtd + "x " + nomeExibicao(caminho),
                venda ? Color.valueOf("7fd47f") : Color.valueOf("7fb4ff"))).left().row();
            Table precoLinha = new Table();
            precoLinha.add(valorComMoedas(preco)).padRight(4);
            precoLinha.add(linhaStat("each", Color.GRAY));
            info.add(precoLinha).left().row();
            linha.add(info).growX().left();
            linha.add(botaoSpells("Cancel", "vermelho", () -> emitirMercado("market_cancel", w -> w.set("id", id))))
                .width(110).height(34);
            lista.add(linha).growX().padBottom(3).row();
        }
        ScrollPane rolagemMinhas = rolagem(lista);
        rolagemComRodinha(rolagemMinhas);
        mercadoDetalhe.add(rolagemMinhas).grow().row();
        if (!mercadoAviso.isEmpty()) mercadoDetalhe.add(linhaStat(mercadoAviso, Color.valueOf("ffd56b"))).left().padTop(4).row();
    }

    // ===================== RANK =====================
    // Abas: Level (level + XP total) e as skills (Defense, Magic, Focus,
    // Musicality, Melee - o level da skill). Linha: posicao, icone da classe,
    // nome na cor da classe, valores na direita. A minha linha fica destacada.
    // Servidor: servidor.py::handle_get_ranking (top 50, atualiza 1x por hora).
    private final Table rankPage = new Table();
    private final Table listaRank = new Table();
    private String categoriaRank = "level";
    private static final String[][] CATEGORIAS_RANK = {
        {"level", "Level"}, {"defense", "Defense"}, {"magic", "Magic"},
        {"focus", "Focus"}, {"musicality", "Musicality"}, {"melee", "Melee"}
    };

    private void construirPaginaRank() {
        rankPage.top();
        // Uma caixa de selecao so' (mesmo estilo do zoom nas opcoes).
        // Icones iguais aos da aba Skills na frente de cada opcao.
        Map<String, TextureRegion> icones = new HashMap<>();
        String[] regioes = {"ui/XPIcon", "ui/DefenseIcon", "ui/items/StarterStaff", "ui/FocusIcon", "ui/MusicalityIcon",
            atlas.findRegion("ui/MeeleIcon") != null ? "ui/MeeleIcon" : "ui/MeleeIcon"};
        for (int i = 0; i < CATEGORIAS_RANK.length; i++) {
            TextureAtlas.AtlasRegion r = atlas.findRegion(regioes[i]);
            if (r != null) icones.put(CATEGORIAS_RANK[i][1], r);
        }
        com.badlogic.gdx.scenes.scene2d.ui.SelectBox<String> escolha = new SelectComIcone(skin, "zoom-select", icones);
        String[] nomes = new String[CATEGORIAS_RANK.length];
        for (int i = 0; i < nomes.length; i++) nomes[i] = CATEGORIAS_RANK[i][1];
        escolha.setItems(nomes);
        escolha.setAlignment(Align.center);
        escolha.getList().setAlignment(Align.center);
        escolha.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                categoriaRank = CATEGORIAS_RANK[escolha.getSelectedIndex()][0];
                listaRank.clearChildren();
                pedirRanking();
            }
        });
        Table abas = new Table();
        abas.left();
        abas.add(escolha).width(180).height(36);
        listaRank.top();
        ScrollPane scroll = new ScrollPane(listaRank, skin);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        scroll.setOverscroll(false, false);
        Table caixa = new Table();
        caixa.setBackground(UiSkin.retangulo(new Color(0.08f, 0.08f, 0.08f, 1f), new Color(0.3f, 0.3f, 0.3f, 1f), 1));
        caixa.top();
        caixa.add(scroll).grow().pad(4);
        Label aviso = new LabelPixel("Rankings update every hour.", skin.get("hud", Label.LabelStyle.class));
        escalarFonte(aviso, 0.5f * FONTE_STATS);
        aviso.setColor(Color.GRAY);
        rankPage.add(abas).left().padBottom(6).row();
        rankPage.add(caixa).grow().row();
        rankPage.add(aviso).right().padTop(3);
    }

    /** SelectBox com um icone antes do texto (na caixa e na lista aberta). */
    private static final class SelectComIcone extends com.badlogic.gdx.scenes.scene2d.ui.SelectBox<String> {
        private final Map<String, TextureRegion> icones;

        SelectComIcone(Skin skin, String estilo, Map<String, TextureRegion> icones) {
            super(skin, estilo);
            this.icones = icones;
        }

        static com.badlogic.gdx.graphics.g2d.GlyphLayout desenhar(Map<String, TextureRegion> icones,
                com.badlogic.gdx.graphics.g2d.Batch batch, com.badlogic.gdx.graphics.g2d.BitmapFont fonte,
                String item, float x, float y, float largura) {
            TextureRegion icone = icones != null ? icones.get(item) : null;
            com.badlogic.gdx.graphics.g2d.GlyphLayout texto = new com.badlogic.gdx.graphics.g2d.GlyphLayout(fonte, item);
            float tam = fonte.getLineHeight() * 0.9f, espaco = icone != null ? tam * 0.3f : 0f;
            float total = (icone != null ? tam : 0f) + espaco + texto.width;
            float inicio = x + Math.max(0f, (largura - total) / 2f);
            if (icone != null) {
                // y e' o topo do texto: centraliza o icone na altura das letras.
                float meio = y - fonte.getCapHeight() / 2f;
                com.badlogic.gdx.graphics.Color antes = new com.badlogic.gdx.graphics.Color(batch.getColor());
                batch.setColor(1f, 1f, 1f, antes.a);
                batch.draw(icone, inicio, meio - tam / 2f, tam, tam);
                batch.setColor(antes);
            }
            return fonte.draw(batch, item, inicio + (icone != null ? tam : 0f) + espaco, y);
        }

        @Override protected com.badlogic.gdx.graphics.g2d.GlyphLayout drawItem(com.badlogic.gdx.graphics.g2d.Batch batch,
                com.badlogic.gdx.graphics.g2d.BitmapFont fonte, String item, float x, float y, float largura) {
            return desenhar(icones, batch, fonte, item, x, y, largura);
        }

        @Override protected SelectBoxScrollPane<String> newScrollPane() {
            return new SelectBoxScrollPane<String>(this) {
                @Override protected com.badlogic.gdx.scenes.scene2d.ui.List<String> newList() {
                    return new com.badlogic.gdx.scenes.scene2d.ui.List<String>(SelectComIcone.this.getStyle().listStyle) {
                        @Override public String toString(String item) { return item; }
                        @Override protected com.badlogic.gdx.graphics.g2d.GlyphLayout drawItem(com.badlogic.gdx.graphics.g2d.Batch batch,
                                com.badlogic.gdx.graphics.g2d.BitmapFont fonte, int indice, String item, float x, float y, float largura) {
                            return desenhar(icones, batch, fonte, item, x, y, largura);
                        }
                    };
                }
            };
        }
    }

    private void pedirRanking() {
        if (!socket.isConnected()) return;
        String cat = categoriaRank;
        socket.emitRaw("get_ranking", GameSocket.obj(w -> w.set("category", cat)));
    }

    /** Resposta do servidor: {category, entries: [{name, class, level, exp, value?}]}. */
    public void atualizarRanking(JsonValue dados) {
        if (dados == null || !categoriaRank.equals(dados.getString("category", ""))) return;
        if (rankPage.getChildren().size == 0) construirPaginaRank();
        listaRank.clearChildren();
        boolean porLevel = "level".equals(categoriaRank);
        JsonValue lista = dados.get("entries");
        int pos = 0;
        if (lista != null) {
            for (JsonValue e = lista.child; e != null; e = e.next) {
                pos++;
                String nome = e.getString("name", "?"), classe = e.getString("class", "Knight");
                boolean eu = nome.equals(nomeLocal);
                Table linha = new Table();
                linha.setBackground(UiSkin.retangulo(
                    eu ? new Color(0.22f, 0.18f, 0.05f, 1f) : pos % 2 == 0 ? new Color(0.12f, 0.12f, 0.12f, 1f) : new Color(0.09f, 0.09f, 0.09f, 1f),
                    eu ? Color.valueOf("f5d328") : new Color(0.16f, 0.16f, 0.16f, 1f), 1));
                // Top 3 em ouro/prata/bronze.
                Label posicao = new LabelPixel("#" + pos, skin.get("hud", Label.LabelStyle.class));
                escalarFonte(posicao, 0.6f * FONTE_STATS);
                posicao.setColor(pos == 1 ? Color.valueOf("f5d328") : pos == 2 ? Color.valueOf("c8d0d8")
                    : pos == 3 ? Color.valueOf("d08a4a") : Color.GRAY);
                linha.add(posicao).width(40).left().padLeft(6);
                TextureAtlas.AtlasRegion icone = atlas.findRegion(iconeClasse(classe));
                if (icone != null) {
                    Image i = new Image(new TextureRegionDrawable(icone));
                    i.setScaling(Scaling.fit);
                    linha.add(i).size(24).padRight(8);
                }
                Label rotuloNome = new LabelPixel(nome, skin.get("hud", Label.LabelStyle.class));
                escalarFonte(rotuloNome, 0.7f * FONTE_STATS);
                rotuloNome.setColor(com.teste.game.ChatUI.corDaClasse(classe));
                linha.add(rotuloNome).left().expandX();
                if (porLevel) {
                    Label xp = new LabelPixel(formatarNumero(e.getLong("exp", 0)) + " XP", skin.get("hud", Label.LabelStyle.class));
                    escalarFonte(xp, 0.55f * FONTE_STATS);
                    xp.setColor(Color.LIGHT_GRAY);
                    linha.add(xp).right().padRight(12);
                    Label lv = new LabelPixel("Lv " + e.getInt("level", 1), skin.get("hud", Label.LabelStyle.class));
                    escalarFonte(lv, 0.7f * FONTE_STATS);
                    linha.add(lv).right().width(70).padRight(6);
                } else {
                    Label valor = new LabelPixel(String.valueOf(e.getInt("value", 10)), skin.get("hud", Label.LabelStyle.class));
                    escalarFonte(valor, 0.7f * FONTE_STATS);
                    linha.add(valor).right().width(70).padRight(6);
                }
                // Clicar no nome abre a janela do player (igual Friends).
                AmigoInfo info = new AmigoInfo(nome);
                info.classe = classe;
                info.skins = e.get("skins");
                infoRanking.put(nome, info);
                if (!eu) {
                    linha.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled);
                    linha.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
                        @Override public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent ev, float x, float y) {
                            if (aoClicarAmigo != null) aoClicarAmigo.accept(nome);
                        }
                    });
                }
                listaRank.add(linha).growX().height(34).padBottom(2).row();
            }
        }
        if (pos == 0) {
            Label vazio = new LabelPixel("No players yet.", skin.get("hud", Label.LabelStyle.class));
            escalarFonte(vazio, 0.6f * FONTE_STATS);
            vazio.setColor(Color.GRAY);
            listaRank.add(vazio).padTop(20);
        }
    }

    // ===================== BESTIARY =====================
    // Grade de mobs (numero + imagem de frente + medalha atual) e, a direita,
    // a ficha do selecionado: foto quadrada maior com as 3 medalhas no canto,
    // nome, e o que ja foi liberado matando (servidor.py::montar_bestiario):
    // 20 = HP, 100 = itens do loot, 500 = chance de cada item.
    private final Table bestiaryPage = new Table();
    private final Table gradeBestiario = new Table();
    private final Table fichaBestiario = new Table();
    private JsonValue entradasBestiario;
    private int bestiarioSelecionado = -1; // "id" da entrada
    private static final String[] MEDALHAS = {"sheet/r30_c9", "sheet/r31_c13", "sheet/r32_c7"}; // cobre, prata, ouro
    private static final int COLUNAS_BESTIARIO = 4;
    private static final float SLOT_BESTIARIO = 58f;

    /** Medalha do nivel (2 = cobre, 3 = prata, 4 = ouro), ou null. */
    public TextureAtlas.AtlasRegion medalhaDoNivel(int nivel) {
        return nivel >= 2 && nivel <= 4 ? atlas.findRegion(MEDALHAS[nivel - 2]) : null;
    }

    /** Lista do servidor: {entries: [{id, tier, type?, name?, kills?, hp?, loot?}]}. */
    public void atualizarBestiario(JsonValue dados) {
        entradasBestiario = dados != null ? dados.get("entries") : null;
        if ("Bestiary".equals(secaoAtual) && root.isVisible()) reconstruirBestiario();
    }

    /** Matou mais um (sem subir de medalha): so' troca o contador. */
    public void atualizarKillsBestiario(String tipo, int kills) {
        if (entradasBestiario == null || tipo == null) return;
        for (JsonValue e = entradasBestiario.child; e != null; e = e.next) {
            if (!tipo.equals(e.getString("type", null))) continue;
            JsonValue k = e.get("kills");
            if (k != null) k.set(kills, null);
            else e.addChild("kills", new JsonValue(kills));
        }
        if ("Bestiary".equals(secaoAtual) && root.isVisible()) reconstruirFichaBestiario();
    }

    private TextureAtlas.AtlasRegion tiraDoMob(String tipo) {
        if (tipo == null || tipo.isEmpty()) return null;
        TextureAtlas.AtlasRegion tira = atlas.findRegion("sprites/mobs/" + tipo);
        if (tira == null) tira = atlas.findRegion("sprites/mobs/" + Character.toUpperCase(tipo.charAt(0)) + tipo.substring(1));
        if (tira == null) {
            // Tipo vem minusculo do servidor ("spidermushroom" -> SpiderMushroom).
            for (TextureAtlas.AtlasRegion r : atlas.getRegions()) {
                if (r.name.equalsIgnoreCase("sprites/mobs/" + tipo)) return r;
            }
        }
        return tira;
    }

    /** Mob andando pra baixo (quadros 4 e 5 da tira, igual o mundo), em
     * escala que cabe no espaco; tira curta demais: so' o quadro que tiver. */
    private final class MobAnimado extends com.badlogic.gdx.scenes.scene2d.ui.Widget {
        private final TextureRegion[] quadros;
        private float tempo = 0f;
        MobAnimado(TextureAtlas.AtlasRegion tira) {
            int n = tira.getRegionWidth() / SkinsUtil.larguraQuadro(tira);
            if (n >= 6) quadros = new TextureRegion[]{SkinsUtil.quadro(tira, 4), SkinsUtil.quadro(tira, 5)};
            else if (n >= 4) quadros = new TextureRegion[]{SkinsUtil.quadro(tira, SkinsUtil.FRAME_BAIXO)};
            else quadros = new TextureRegion[]{tira};
        }
        @Override public void act(float delta) {
            super.act(delta);
            tempo += delta;
        }
        @Override public void draw(com.badlogic.gdx.graphics.g2d.Batch batch, float parentAlpha) {
            TextureRegion q = quadros[(int) (tempo / 0.55f) % quadros.length]; // passo lento, so' pra dar vida
            float escala = Math.min(getWidth() / q.getRegionWidth(), getHeight() / q.getRegionHeight());
            if (escala >= 2f) escala = (float) Math.floor(escala); // pixel art sem distorcer
            float w = q.getRegionWidth() * escala, h = q.getRegionHeight() * escala;
            Color c = getColor();
            batch.setColor(c.r, c.g, c.b, c.a * parentAlpha);
            batch.draw(q, getX() + (getWidth() - w) / 2f, getY() + (getHeight() - h) / 2f, w, h);
            batch.setColor(Color.WHITE);
        }
    }

    /** Moeda em cobre -> maior moeda que cabe (100 cobre = 1 prata...). */
    private String[] moedaFormatada(long cobre) {
        String[] tipos = {"Platinum", "Gold", "Silver", "Copper"};
        long[] valores = {1_000_000L, 10_000L, 100L, 1L};
        for (int i = 0; i < tipos.length; i++) {
            if (cobre >= valores[i] || i == tipos.length - 1) {
                float v = cobre / (float) valores[i];
                String texto = v == Math.round(v) ? String.valueOf(Math.round(v))
                    : String.format(java.util.Locale.US, "%.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
                return new String[]{tipos[i], texto};
            }
        }
        return new String[]{"Copper", String.valueOf(cobre)};
    }

    private void construirPaginaBestiario() {
        bestiaryPage.clearChildren();
        bestiaryPage.top().left();
        gradeBestiario.top().left();
        ScrollPane scroll = new ScrollPane(gradeBestiario, skin);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        Table caixaGrade = new Table();
        caixaGrade.setBackground(UiSkin.retangulo(new Color(0.08f, 0.08f, 0.08f, 1f), new Color(0.3f, 0.3f, 0.3f, 1f), 1));
        caixaGrade.add(scroll).grow().pad(4);
        fichaBestiario.top();
        fichaBestiario.setBackground(UiSkin.retangulo(new Color(0.08f, 0.08f, 0.08f, 1f), new Color(0.3f, 0.3f, 0.3f, 1f), 1));
        bestiaryPage.add(caixaGrade).width(COLUNAS_BESTIARIO * (SLOT_BESTIARIO + 4) + 26).growY().padRight(8);
        bestiaryPage.add(fichaBestiario).grow();
    }

    private void reconstruirBestiario() {
        if (bestiaryPage.getChildren().size == 0) construirPaginaBestiario();
        gradeBestiario.clearChildren();
        int coluna = 0;
        if (entradasBestiario != null) {
            for (JsonValue e = entradasBestiario.child; e != null; e = e.next) {
                gradeBestiario.add(slotBestiario(e)).size(SLOT_BESTIARIO).pad(2);
                if (++coluna % COLUNAS_BESTIARIO == 0) gradeBestiario.row();
            }
        }
        reconstruirFichaBestiario();
    }

    private Table slotBestiario(JsonValue e) {
        int id = e.getInt("id", 0), nivel = e.getInt("tier", 0);
        boolean descoberto = nivel >= 1;
        boolean selecionado = id == bestiarioSelecionado;
        Table fundo = new Table();
        fundo.setBackground(UiSkin.retangulo(
            descoberto ? new Color(0.13f, 0.13f, 0.16f, 1f) : new Color(0.07f, 0.07f, 0.07f, 1f),
            selecionado ? Color.valueOf("f0a028") : new Color(0.32f, 0.32f, 0.36f, 1f), selecionado ? 2 : 1));
        if (!descoberto) return fundo; // ainda nao matou: slot vazio
        Table camadaImagem = new Table();
        TextureAtlas.AtlasRegion tira = tiraDoMob(e.getString("type", ""));
        if (tira != null) camadaImagem.add(new MobAnimado(tira)).size(SLOT_BESTIARIO * 0.62f).padTop(6);
        Table camadaNumero = new Table();
        camadaNumero.top().left();
        Label numero = new LabelPixel(String.valueOf(id), skin.get("hud", Label.LabelStyle.class));
        escalarFonte(numero, 0.65f);
        camadaNumero.add(numero).pad(2, 4, 0, 0);
        Table camadaMedalha = new Table();
        camadaMedalha.bottom().right();
        TextureAtlas.AtlasRegion medalha = medalhaDoNivel(nivel);
        if (medalha != null) {
            Image m = new Image(new TextureRegionDrawable(medalha));
            m.setScaling(Scaling.fit);
            camadaMedalha.add(m).size(SLOT_BESTIARIO * 0.32f).pad(0, 0, 2, 2);
        }
        Stack pilha = new Stack(fundo, camadaImagem, camadaNumero, camadaMedalha);
        Table slot = new Table();
        slot.add(pilha).grow();
        slot.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled);
        slot.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            @Override public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent ev, float x, float y) {
                bestiarioSelecionado = id;
                reconstruirBestiario();
            }
        });
        return slot;
    }

    private JsonValue entradaBestiario(int id) {
        if (entradasBestiario == null) return null;
        for (JsonValue e = entradasBestiario.child; e != null; e = e.next) if (e.getInt("id", 0) == id) return e;
        return null;
    }

    private void reconstruirFichaBestiario() {
        fichaBestiario.clearChildren();
        JsonValue e = entradaBestiario(bestiarioSelecionado);
        if (e == null || e.getInt("tier", 0) < 1) {
            Label vazio = new LabelPixel("Defeat monsters to\nfill your bestiary.", skin.get("hud", Label.LabelStyle.class));
            escalarFonte(vazio, 0.6f * FONTE_STATS);
            vazio.setAlignment(Align.center);
            vazio.setColor(Color.GRAY);
            fichaBestiario.add(vazio).expand().center();
            return;
        }
        int nivel = e.getInt("tier", 0);
        float lado = 150f;
        fichaBestiario.left().top();
        // Foto quadrada (mob andando pra baixo) + as 3 medalhas no canto de
        // cima a direita (apagadas as que ainda nao tem) + mortes no canto
        // de baixo a esquerda.
        Table foto = new Table();
        foto.setBackground(UiSkin.retangulo(new Color(0.24f, 0.24f, 0.24f, 1f), new Color(0.45f, 0.45f, 0.45f, 1f), 2));
        TextureAtlas.AtlasRegion tira = tiraDoMob(e.getString("type", ""));
        if (tira != null) foto.add(new MobAnimado(tira)).size(lado * 0.62f);
        Table medalhas = new Table();
        for (int i = 0; i < 3; i++) {
            TextureAtlas.AtlasRegion r = atlas.findRegion(MEDALHAS[i]);
            if (r == null) continue;
            Image m = new Image(new TextureRegionDrawable(r));
            m.setScaling(Scaling.fit);
            if (nivel < i + 2) m.setColor(0.25f, 0.25f, 0.25f, 0.6f); // ainda nao ganhou
            medalhas.add(m).size(22).padLeft(1);
        }
        Table cantoMedalhas = new Table();
        cantoMedalhas.top().right();
        cantoMedalhas.add(medalhas).pad(5, 0, 0, 5);
        Table cantoKills = new Table();
        cantoKills.bottom().left();
        TextureAtlas.AtlasRegion iconeKills = atlas.findRegion("ui/CritIcon");
        if (iconeKills != null) {
            Image i = new Image(new TextureRegionDrawable(iconeKills));
            i.setScaling(Scaling.fit);
            cantoKills.add(i).size(20).padRight(4);
        }
        Label kills = new LabelPixel(String.valueOf(e.getInt("kills", 0)), skin.get("hud", Label.LabelStyle.class));
        escalarFonte(kills, 0.75f * FONTE_STATS);
        cantoKills.add(kills);
        cantoKills.pad(0, 6, 4, 0);
        fichaBestiario.add(new Stack(foto, cantoMedalhas, cantoKills)).size(lado).pad(8, 10, 0, 10).center().row();

        // Tudo abaixo da foto rola (loot grande nao vaza da janela).
        Table info = new Table();
        info.top().left();
        ScrollPane rolagem = new ScrollPane(info, skin);
        rolagem.setFadeScrollBars(false);
        rolagem.setScrollingDisabled(true, false);
        rolagem.setOverscroll(false, false);
        rolagem.setForceScroll(false, true); // barra sempre ali, mesmo sem passar do espaco

        Label nome = new LabelPixel(e.getString("name", "?"), skin.get("hud", Label.LabelStyle.class));
        escalarFonte(nome, 1.0f * FONTE_STATS);
        info.add(nome).left().padLeft(10).padTop(6).row();

        if (e.has("hp")) {
            Table linhaHp = new Table();
            TextureAtlas.AtlasRegion iconeHp = atlas.findRegion("ui/HPIcon");
            if (iconeHp != null) {
                Image i = new Image(new TextureRegionDrawable(iconeHp));
                i.setScaling(Scaling.fit);
                linhaHp.add(i).size(ICONE_FICHA).padRight(6);
            }
            Label hp = new LabelPixel(String.valueOf(e.getInt("hp", 0)), skin.get("hud", Label.LabelStyle.class));
            escalarFonte(hp, 0.75f * FONTE_STATS);
            hp.setColor(1f, 0.55f, 0.55f, 1f); // vermelho claro
            linhaHp.add(hp);
            info.add(linhaHp).left().padLeft(10).padTop(6).row();
            // Bonus da medalha de ouro (apagado ate' ganhar).
            boolean comBonus = nivel >= 4;
            Table linhaBonus = new Table();
            TextureAtlas.AtlasRegion estrela = atlas.findRegion("ui/ClassIcon");
            if (estrela != null) {
                Image i = new Image(new TextureRegionDrawable(estrela));
                i.setScaling(Scaling.fit);
                if (!comBonus) i.setColor(0.4f, 0.4f, 0.4f, 0.7f);
                linhaBonus.add(i).size(ICONE_FICHA).padRight(6);
            }
            Label bonus = new LabelPixel("5% Extra DMG/DEF", skin.get("hud", Label.LabelStyle.class));
            escalarFonte(bonus, 0.65f * FONTE_STATS);
            bonus.setColor(comBonus ? Color.valueOf("f5d328") : Color.GRAY);
            linhaBonus.add(bonus);
            info.add(linhaBonus).left().padLeft(10).padTop(4).row();
        }
        JsonValue loot = e.get("loot");
        if (loot != null) {
            boolean comChance = nivel >= 4;
            // "Loot" em cinza com o icone da bag comum (a do chao).
            Table linhaTitulo = new Table();
            TextureAtlas.AtlasRegion iconeBag = atlas.findRegion("ui/currency/BasicBag");
            if (iconeBag != null) {
                Image i = new Image(new TextureRegionDrawable(iconeBag));
                i.setScaling(Scaling.fit);
                linhaTitulo.add(i).size(ICONE_FICHA).padRight(6);
            }
            Label titulo = new LabelPixel("Loot", skin.get("hud", Label.LabelStyle.class));
            escalarFonte(titulo, 0.75f * FONTE_STATS);
            titulo.setColor(Color.LIGHT_GRAY);
            linhaTitulo.add(titulo);
            info.add(linhaTitulo).left().padLeft(10).padTop(8).row();
            // Moedas (sempre caem): icone da moeda + minimo - maximo,
            // convertido pra maior moeda (200 cobre = 2 prata).
            JsonValue moeda = e.get("currency");
            if (moeda != null) {
                Table linhaMoeda = new Table();
                String[] min = moedaFormatada(moeda.getLong("min", 0)), max = moedaFormatada(moeda.getLong("max", 0));
                adicionarMoedaLoot(linhaMoeda, min);
                Label traco = new LabelPixel(" - ", skin.get("hud", Label.LabelStyle.class));
                escalarFonte(traco, 0.7f * FONTE_STATS);
                linhaMoeda.add(traco);
                adicionarMoedaLoot(linhaMoeda, max);
                if (comChance) {
                    Label cem = new LabelPixel("  100%", skin.get("hud", Label.LabelStyle.class));
                    escalarFonte(cem, 0.65f * FONTE_STATS);
                    cem.setColor(Color.LIGHT_GRAY);
                    linhaMoeda.add(cem);
                }
                info.add(linhaMoeda).left().padLeft(10).padTop(4).row();
            }
            Table itens = new Table();
            itens.left();
            int n = 0;
            for (JsonValue d = loot.child; d != null; d = d.next) {
                Table celula = new Table();
                TextureAtlas.AtlasRegion icone = iconeDoItem(d.getString("item", ""));
                Table quadro = new Table();
                quadro.setBackground(UiSkin.retangulo(new Color(0.13f, 0.13f, 0.16f, 1f), new Color(0.32f, 0.32f, 0.36f, 1f), 1));
                if (icone != null) {
                    Image i = new Image(new TextureRegionDrawable(icone));
                    i.setScaling(Scaling.fit);
                    quadro.add(i).size(30);
                }
                // Mouse em cima / dedo segurando: popup com nome, level e stats.
                List<String> variantes = new ArrayList<>();
                JsonValue vs = d.get("variants");
                if (vs != null && vs.isArray()) for (JsonValue v = vs.child; v != null; v = v.next) variantes.add(v.asString());
                if (variantes.isEmpty()) variantes.add(d.getString("item", ""));
                ligarPopupItem(quadro, variantes);
                celula.add(quadro).size(38).row();
                if (comChance && d.has("chance")) {
                    Label chance = new LabelPixel(formatarChance(d.getFloat("chance", 0f)), skin.get("hud", Label.LabelStyle.class));
                    escalarFonte(chance, 0.6f * FONTE_STATS);
                    celula.add(chance).padTop(1);
                }
                itens.add(celula).pad(2);
                if (++n % 4 == 0) itens.row();
            }
            if (n > 0) info.add(itens).left().padLeft(8).padTop(4).row();
            else if (moeda == null) {
                Label nada = new LabelPixel("Nothing", skin.get("hud", Label.LabelStyle.class));
                escalarFonte(nada, 0.7f * FONTE_STATS);
                nada.setColor(Color.GRAY);
                info.add(nada).left().padLeft(10).row();
            }
        }
        fichaBestiario.add(rolagem).grow().padBottom(6);
    }

    // ===== Popup de item do bestiary =====
    private Table popupItem;
    private static final float LARGURA_POPUP_ITEM = 230f;
    private static final float TROCA_VARIANTE_SEG = 1f; // anel/colar: alterna o ADC sorteado

    /** Mouse em cima (PC) ou dedo segurando (celular) mostra o popup do item;
     * com mais de uma variacao (Ruby Ring de Melee/Magic/...) alterna a cada 1s. */
    private void ligarPopupItem(final com.badlogic.gdx.scenes.scene2d.Actor ancora, final List<String> caminhos) {
        ancora.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled);
        ancora.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
            @Override public void enter(com.badlogic.gdx.scenes.scene2d.InputEvent e, float x, float y, int ponteiro,
                                        com.badlogic.gdx.scenes.scene2d.Actor de) {
                if (ponteiro == -1) mostrarPopupItem(ancora, caminhos); // -1 = mouse sem botao
            }
            @Override public void exit(com.badlogic.gdx.scenes.scene2d.InputEvent e, float x, float y, int ponteiro,
                                       com.badlogic.gdx.scenes.scene2d.Actor para) {
                if (ponteiro == -1) esconderPopupItem();
            }
            @Override public boolean touchDown(com.badlogic.gdx.scenes.scene2d.InputEvent e, float x, float y, int ponteiro, int botao) {
                mostrarPopupItem(ancora, caminhos);
                return true;
            }
            @Override public void touchUp(com.badlogic.gdx.scenes.scene2d.InputEvent e, float x, float y, int ponteiro, int botao) {
                // Celular: some ao soltar. PC: continua enquanto o mouse estiver em cima.
                if (Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android) esconderPopupItem();
            }
        });
    }

    private void mostrarPopupItem(final com.badlogic.gdx.scenes.scene2d.Actor ancora, final List<String> caminhos) {
        esconderPopupItem();
        final Stage stage = root.getStage();
        if (caminhos.isEmpty() || stage == null) return;
        final Table caixa = new Table();
        caixa.setBackground(UiSkin.retangulo(COR_POPUP_FUNDO, COR_POPUP_BORDA, 2));
        caixa.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
        final Table bloco = new Table();
        caixa.add(bloco).width(LARGURA_POPUP_ITEM).pad(8);
        final int[] indice = {0};
        final Runnable preencher = () -> {
            preencherBlocoStats(bloco, caminhos.get(indice[0] % caminhos.size()), null);
            caixa.pack();
            posicionarPopupItem(caixa, ancora);
        };
        preencher.run();
        // Some sozinho se o livro fechar / a ficha for refeita com ele aberto.
        caixa.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.forever(
            com.badlogic.gdx.scenes.scene2d.actions.Actions.run(() -> {
                if (ancora.getStage() == null || !root.isVisible()) esconderPopupItem();
            })));
        if (caminhos.size() > 1) {
            caixa.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.forever(
                com.badlogic.gdx.scenes.scene2d.actions.Actions.sequence(
                    com.badlogic.gdx.scenes.scene2d.actions.Actions.delay(TROCA_VARIANTE_SEG),
                    com.badlogic.gdx.scenes.scene2d.actions.Actions.run(() -> { indice[0]++; preencher.run(); }))));
        }
        popupItem = caixa;
        stage.addActor(caixa);
    }

    /** Do lado direito do icone (ou do esquerdo, se nao couber), dentro da tela. */
    private void posicionarPopupItem(Table caixa, com.badlogic.gdx.scenes.scene2d.Actor ancora) {
        Stage stage = root.getStage();
        if (stage == null) return;
        com.badlogic.gdx.math.Vector2 p = ancora.localToStageCoordinates(new com.badlogic.gdx.math.Vector2(0f, 0f));
        float w = caixa.getWidth(), h = caixa.getHeight();
        float x = p.x + ancora.getWidth() + 6f;
        if (x + w > stage.getWidth()) x = p.x - w - 6f;
        float y = p.y + ancora.getHeight() - h;
        x = Math.max(0f, Math.min(x, stage.getWidth() - w));
        y = Math.max(0f, Math.min(y, stage.getHeight() - h));
        caixa.setPosition(Math.round(x), Math.round(y));
    }

    private void esconderPopupItem() {
        if (popupItem != null) {
            popupItem.remove();
            popupItem = null;
        }
    }

    private void adicionarMoedaLoot(Table linha, String[] moeda) {
        TextureAtlas.AtlasRegion r = atlas.findRegion("ui/currency/" + moeda[0]);
        if (r != null) {
            Image i = new Image(new TextureRegionDrawable(r));
            i.setScaling(Scaling.fit);
            linha.add(i).size(ICONE_FICHA).padRight(4);
        }
        Label v = new LabelPixel(moeda[1], skin.get("hud", Label.LabelStyle.class));
        escalarFonte(v, 0.75f * FONTE_STATS);
        v.setColor(corMoeda(moeda[0]));
        linha.add(v);
    }

    /** Mesmo tamanho de icone em todas as linhas da ficha (HP, bonus, loot, moedas). */
    private static final float ICONE_FICHA = 22f;

    private static String formatarChance(float chance) {
        float pct = chance * 100f;
        if (pct >= 10f || pct == Math.round(pct)) return Math.round(pct) + "%";
        return String.format(java.util.Locale.US, pct >= 1f ? "%.1f%%" : "%.2f%%", pct);
    }

    private void construirPaginaSpells() {
        conteudoMagias = null;
        spellsPage.clearChildren();
        spellsPage.top().left();
        int visiveis = slotsVisiveis();
        if (slotAtalhoEscolhendo >= visiveis) slotAtalhoEscolhendo = -1;

        // ---- Esquerda: slots ----
        Table esquerda = painelSpells();
        esquerda.add(linhaStat("Hotbar", Color.LIGHT_GRAY)).left().padBottom(6).row();
        Table grade = new Table();
        grade.top().left();
        for (int i = 0; i < visiveis; i++) {
            final int indice = i;
            String tecla = ehMobile() ? String.valueOf(i + 1) : Controles.nomeTecla(Controles.tecla("hotbar" + (i + 1)));
            grade.add(slotAtalho(atalhos[i], i == slotAtalhoEscolhendo, tecla, () -> {
                slotAtalhoEscolhendo = slotAtalhoEscolhendo == indice ? -1 : indice;
                construirPaginaSpells();
            })).size(TAM_SLOT_ATALHO).pad(3);
            if ((i + 1) % 3 == 0) grade.row();
        }
        esquerda.add(grade).left().row();
        esquerda.add().grow();

        // ---- Direita: o que colocar no slot escolhido ----
        Table direita = painelSpells();
        if (slotAtalhoEscolhendo < 0) {
            direita.add(linhaStat("Select a slot on the left", Color.GRAY)).expand().center();
        } else {
            final int slot = slotAtalhoEscolhendo;
            direita.add(linhaStat("Slot " + (ehMobile() ? String.valueOf(slot + 1)
                : Controles.nomeTecla(Controles.tecla("hotbar" + (slot + 1)))), Color.WHITE)).left().padBottom(8).row();
            Table abas = new Table();
            abas.left();
            abas.add(botaoSpells("Inventory", escolhendoMagias ? "default" : "verde", () -> {
                escolhendoMagias = false;
                construirPaginaSpells();
            })).size(110, 34).padRight(6);
            abas.add(botaoSpells("Spells", escolhendoMagias ? "verde" : "default", () -> {
                escolhendoMagias = true;
                construirPaginaSpells();
            })).size(110, 34);
            direita.add(abas).left().padBottom(10).row();

            Table conteudo = new Table();
            conteudo.top().left();
            conteudoMagias = escolhendoMagias ? conteudo : null;
            if (escolhendoMagias) {
                // Uma "gaveta" por magia: icone, nome, mana, descricao e dano.
                for (String magia : magiasConhecidas) {
                    // padRight: a borda da direita da gaveta ficava cortada pela rolagem.
                    conteudo.add(gavetaMagia(magia, magia.equals(atalhos[slot]), () -> escolherAtalho(slot, magia)))
                        .growX().padBottom(6).padRight(3).row();
                }
                if (magiasConhecidas.isEmpty()) conteudo.add(linhaStat("You don't know any spells yet.", Color.GRAY)).left();
            } else {
                java.util.LinkedHashSet<String> caminhos = new java.util.LinkedHashSet<>();
                for (InventoryItem it : inventoryItems) if (podeIrNoSlot(it.itemPath, slot)) caminhos.add(it.itemPath);
                int n = 0;
                for (String caminho : caminhos) {
                    conteudo.add(slotAtalho(caminho, caminho.equals(atalhos[slot]), null, () -> escolherAtalho(slot, caminho)))
                        .size(TAM_SLOT_ATALHO).pad(3);
                    if (++n % 4 == 0) conteudo.row();
                }
                if (caminhos.isEmpty()) conteudo.add(linhaStat("No food or potions in your bag.", Color.GRAY)).left();
            }
            ScrollPane rolagem = new ScrollPane(conteudo, skin);
            rolagem.setFadeScrollBars(false);
            rolagem.setScrollingDisabled(true, false);
            rolagemComRodinha(rolagem);
            direita.add(rolagem).grow().left().top().row();
            if (atalhos[slot] != null && !atalhos[slot].isEmpty()) {
                direita.add(botaoSpells("Clear slot", "vermelho", () -> escolherAtalho(slot, ""))).size(130, 34).left().padTop(8);
            }
        }

        spellsPage.add(esquerda).width(TAM_SLOT_ATALHO * 3f + 6f * 3f + 22f).growY().padRight(6);
        spellsPage.add(direita).grow();
    }

    private static final Color COR_MAGIA_FUNDO = new Color(0.22f, 0.09f, 0.01f, 0.94f);
    private static final Color COR_MAGIA_BORDA = new Color(0.72f, 0.33f, 0.06f, 1f);
    private static final Color COR_MAGIA_FUNDO_OVER = new Color(0.28f, 0.12f, 0.02f, 0.96f);
    private static final Color COR_MAGIA_BORDA_OVER = new Color(0.85f, 0.42f, 0.1f, 1f);
    private static final Color COR_MAGIA_SELECIONADA = new Color(1f, 0.78f, 0.3f, 1f);
    private static final Color COR_SANGRAMENTO = Color.valueOf("ff5a5a");
    private static final Color COR_VENENO = Color.valueOf("8cd612");
    // {fundo, borda, fundo hover, borda hover, borda selecionada,
    //  fundo da gaveta, borda da gaveta, fundo da gaveta hover}
    private static final Color[] CORES_SLOT_LARANJA = {COR_MAGIA_FUNDO, COR_MAGIA_BORDA, COR_MAGIA_FUNDO_OVER, COR_MAGIA_BORDA_OVER,
        COR_MAGIA_SELECIONADA, new Color(0.11f, 0.06f, 0.03f, 1f), new Color(0.35f, 0.2f, 0.08f, 1f), new Color(0.16f, 0.08f, 0.03f, 1f)};
    private static final Color[] CORES_SLOT_VERMELHO = {
        new Color(0.24f, 0.03f, 0.03f, 0.94f), new Color(0.75f, 0.14f, 0.12f, 1f),
        new Color(0.30f, 0.05f, 0.05f, 0.96f), new Color(0.88f, 0.22f, 0.18f, 1f),
        new Color(1f, 0.45f, 0.4f, 1f), new Color(0.12f, 0.03f, 0.03f, 1f), new Color(0.42f, 0.1f, 0.1f, 1f), new Color(0.17f, 0.04f, 0.04f, 1f)};

    /** Cores de uma magia (slot e gaveta) - laranja (padrao) ou vermelho
     * (servidor.py "spell_slot"). Indices: ver CORES_SLOT_LARANJA. */
    public Color[] coresSlotMagia(String caminho) {
        EquipStats d = caminho == null ? null : ITEM_STATS.get(caminho);
        if (d == null || d.magiaSlot == null || "orange".equals(d.magiaSlot)) return CORES_SLOT_LARANJA;
        if ("red".equals(d.magiaSlot)) return CORES_SLOT_VERMELHO;
        return coresDoHex(d.magiaSlot);
    }

    private final Map<String, Color[]> cacheCoresHex = new HashMap<>();

    /** Paleta do slot/gaveta a partir de uma cor (ex: "00604b" do Arrow
     * Barrage): a cor e' a borda; fundos sao ela bem escura; hover/selecao, clara. */
    private Color[] coresDoHex(String hex) {
        return cacheCoresHex.computeIfAbsent(hex, h -> {
            Color b = SkinsUtil.cor(h); b.a = 1f;
            java.util.function.BiFunction<Float, Float, Color> tom = (mult, branco) -> {
                Color c = new Color(b).mul(mult, mult, mult, 1f);
                return c.lerp(Color.WHITE, branco);
            };
            Color fundo = tom.apply(0.38f, 0f); fundo.a = 0.94f;
            Color fundoOver = tom.apply(0.5f, 0f); fundoOver.a = 0.96f;
            return new Color[]{fundo, tom.apply(1.15f, 0.05f), fundoOver, tom.apply(1.4f, 0.15f),
                tom.apply(1.4f, 0.45f), tom.apply(0.2f, 0f), tom.apply(0.85f, 0f), tom.apply(0.28f, 0f)};
        });
    }

    /** Dano total: golpes + ticks de sangramento (Heavy Cut: 20 + 3x15 = 65;
     * Arrow Barrage: 3 x 21.5 = 64.5). Sem casa decimal quando e' inteiro. */
    private static String danoTotalMagia(EquipStats d) {
        return porcento(d.magiaDano * d.magiaGolpes + d.sangraTicks * d.sangraPct);
    }

    private static String porcento(float v) {
        return Math.abs(v - Math.round(v)) < 0.01f ? String.valueOf(Math.round(v))
            : String.format(java.util.Locale.US, "%.1f", v);
    }

    /** "3 hits of 21.5%" (so' magia com mais de um golpe). */
    private static String textoGolpes(EquipStats d) { return d.magiaGolpes + " hits of " + porcento(d.magiaDano) + "%"; }

    private static String textoSangramento(EquipStats d) {
        String tipo = "poison".equals(d.magiaDot) ? "Poison" : "Bleed";
        return tipo + ": " + d.sangraTicks + "x " + porcento(d.sangraPct) + "% (every " + porcento(d.sangraCada) + "s)";
    }

    private static Color corDoDot(EquipStats d) { return "poison".equals(d.magiaDot) ? COR_VENENO : COR_SANGRAMENTO; }

    /** Linha clicavel da aba Spells: slot da magia + nome, custo de mana,
     * descricao e % de dano. Borda clara = ja' esta' nesse slot. */
    private static final float TAM_ICONE_GAVETA = 64f;
    /** Magia com o Learn no "Are you sure?" (null = nenhuma). */
    private String magiaArmada;
    private TextButton botaoAprenderRodape;

    /** Botao Learn de baixo (loja de magia): segue a gaveta selecionada. */
    private void atualizarBotaoAprender() {
        if (botaoAprenderRodape == null) return;
        boolean magia = lojaDeMagia() && !abaLojaVenda;
        botaoAprenderRodape.setVisible(magia);
        if (!magia) return;
        ItemLoja it = lojaFoco == null ? null : itemLoja(lojaFoco);
        boolean ok = it != null && ehMagia(it.item) && !conheceMagia(it.item);
        boolean semDinheiro = ok && currencyTotal < it.preco;
        boolean armado = ok && it.item.equals(magiaArmada);
        botaoAprenderRodape.setStyle(!ok || semDinheiro ? estiloBancoBloqueado : estiloPlano(COR_BOTAO_COMPRAR, Color.WHITE));
        botaoAprenderRodape.setText(semDinheiro ? "No Money" : armado ? "Are you sure?" : "Learn");
        iconeDoBotao(botaoAprenderRodape, !armado && !semDinheiro);
    }

    /** 1o clique: "Are you sure?"; 2o: aprende a magia selecionada. */
    private void aprenderMagiaFoco() {
        ItemLoja it = lojaFoco == null ? null : itemLoja(lojaFoco);
        if (it == null || !ehMagia(it.item) || conheceMagia(it.item) || currencyTotal < it.preco || !socket.isConnected()) return;
        if (!it.item.equals(magiaArmada)) { magiaArmada = it.item; atualizarBotaoAprender(); return; }
        magiaArmada = null;
        String npc = lojaNpc, item = it.item;
        socket.emitRaw("shop_buy", GameSocket.obj(w -> {
            w.set("npc_id", npc);
            w.set("item", item);
            w.set("qty", 1);
        }));
        atualizarBotaoAprender();
    }
    private static final Color COR_ALVO_MAGIA = Color.valueOf("ffb366"); // "Single Target"/"Area Damage"
    /** Lista de magias aberta na aba Spells (a janela alarga pro texto caber numa linha). */
    private Table conteudoMagias;

    private Button gavetaMagia(String caminho, boolean noSlot, Runnable aoClicar) {
        return gavetaMagia(caminho, noSlot, aoClicar, 0L);
    }

    /** preco > 0: gaveta da loja (Nyla) - mostra o preco e quebra a descricao
     * na largura do painel (a loja nao alarga a janela pra isso). */
    private Button gavetaMagia(String caminho, boolean noSlot, Runnable aoClicar, long preco) {
        EquipStats dados = ITEM_STATS.get(caminho);
        Button.ButtonStyle estilo = new Button.ButtonStyle();
        Color[] c = coresSlotMagia(caminho);
        estilo.up = UiSkin.retangulo(c[5], noSlot ? c[4] : c[6], 1);
        estilo.over = UiSkin.retangulo(c[7], noSlot ? c[4] : c[1], 1);
        Button gaveta = new Button(estilo);
        gaveta.left().pad(6);
        Button icone = slotAtalho(caminho, noSlot, null, null, false, TAM_ICONE_GAVETA, preco <= 0);
        icone.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled); // o clique vai pra gaveta
        gaveta.add(icone).size(TAM_ICONE_GAVETA).center().padRight(10);
        Table texto = new Table();
        texto.top().left();
        Label nome = new LabelPixel(nomeExibicao(caminho), new Label.LabelStyle(UiSkin.fonteDescricao(skin, 15), Color.WHITE));
        texto.add(nome).left().row();
        if (dados != null) {
            texto.add(linhaComIcone("ui/MPIcon", dados.magiaMana + " Mana", COR_MANA)).left().row();
            if (dados.magiaDescricao != null && !dados.magiaDescricao.isEmpty()) {
                // Aba Spells: numa linha so' (a janela alarga, ajustarLarguraDetalhes).
                // Loja: quebra na largura do painel.
                Label desc = linhaStat(dados.magiaDescricao, Color.LIGHT_GRAY);
                if (preco > 0) { desc.setWrap(true); texto.add(desc).growX().left().row(); }
                else texto.add(desc).left().row();
            }
            Table linhaDano = new Table();
            linhaDano.add(linhaStat(danoTotalMagia(dados) + "% Damage", COR_SET));
            linhaDano.add(linhaStat(dados.magiaArea ? "Area Damage" : "Single Target", COR_ALVO_MAGIA)).padLeft(10);
            texto.add(linhaDano).left().row();
            if (dados.sangraTicks > 0) texto.add(linhaStat(textoSangramento(dados), corDoDot(dados))).left().row();
            if (dados.magiaLento > 0) texto.add(linhaStat("Slows for " + porcento(dados.magiaLento) + "s", Color.LIGHT_GRAY)).left().row();
            if (dados.magiaGolpes > 1) texto.add(linhaStat(textoGolpes(dados), Color.LIGHT_GRAY)).left().row();
        }
        if (preco > 0) {
            // Preco: moedinha + valor na cor da moeda (igual os slots da loja).
            int m = maiorMoeda(preco);
            Table linhaPreco = new Table();
            TextureAtlas.AtlasRegion r = atlas.findRegion("ui/currency/" + MOEDAS_TRADE[m]);
            if (r != null) {
                Image moeda = new Image(new TextureRegionDrawable(r));
                moeda.setScaling(Scaling.fit);
                linhaPreco.add(moeda).size(20).padRight(3);
            }
            Label numero = new LabelPixel(valorNaMoeda(preco, m), new Label.LabelStyle(UiSkin.fontePreco(skin), Color.WHITE));
            numero.setColor(corMoeda(MOEDAS_TRADE[m]));
            linhaPreco.add(numero);
            texto.add(linhaPreco).left().padTop(2).row();
        }
        // Canto inferior direito: "+ (icone) 3 Focus EXP" - golpes na skill por uso
        // (ultima linha do texto, alinhada a' direita: nao alarga a gaveta).
        if (dados != null && dados.magiaExp > 0 && dados.magiaExpSkill != null) {
            String[] sk = skillDaMagia(dados.magiaExpSkill);
            Table exp = new Table();
            Color cor = corSkillPrincipal(dados.magiaExpSkill);
            exp.add(linhaStat("+", cor)).padRight(3);
            TextureAtlas.AtlasRegion r = atlas.findRegion(sk[0]);
            if (r != null) {
                Image ic = new Image(new TextureRegionDrawable(r));
                ic.setScaling(Scaling.fit);
                exp.add(ic).size(16).padRight(3);
            }
            exp.add(linhaStat(dados.magiaExp + " " + sk[1] + " EXP", cor));
            texto.add(exp).right().padTop(2).row();
        }
        gaveta.add(texto).growX().center().left();
        gaveta.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) { aoClicar.run(); }
        });
        return gaveta;
    }

    /** Rodinha do mouse rola a lista quando o mouse esta' em cima dela. */
    private static void rolagemComRodinha(ScrollPane rolagem) {
        rolagem.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
            @Override public void enter(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y, int pointer,
                                        com.badlogic.gdx.scenes.scene2d.Actor de) {
                if (rolagem.getStage() != null) rolagem.getStage().setScrollFocus(rolagem);
            }
        });
    }

    private void escolherAtalho(int slot, String caminho) {
        // Continua com o slot selecionado (da' pra ver o item entrando nele).
        if (socket.isConnected()) {
            socket.emitRaw("set_hotbar", GameSocket.obj(w -> {
                w.set("index", slot);
                w.set("value", caminho);
            }));
        }
        construirPaginaSpells();
    }

    private static final float TAM_SLOT_ATALHO = 54f;

    /** Slot quadrado com o icone do item e a quantidade (caminho null/"" =
     * vazio; sem nenhum na bag = icone transparente e sem numero). */
    private Button slotAtalho(String caminho, boolean selecionado, String tecla, Runnable aoClicar) {
        return slotAtalho(caminho, selecionado, tecla, aoClicar, true, TAM_SLOT_ATALHO);
    }

    /** mostrarMana: custo da magia no canto (a gaveta da aba Spells ja' mostra no texto). */
    private Button slotAtalho(String caminho, boolean selecionado, String tecla, Runnable aoClicar,
                              boolean mostrarMana, float tamanho) {
        return slotAtalho(caminho, selecionado, tecla, aoClicar, mostrarMana, tamanho, true);
    }

    /** apagarIndisponivel: icone transparente se nao tem na bag / nao aprendeu
     * (a loja mostra a magia normal, ela ainda nao foi aprendida). */
    private Button slotAtalho(String caminho, boolean selecionado, String tecla, Runnable aoClicar,
                              boolean mostrarMana, float tamanho, boolean apagarIndisponivel) {
        Button.ButtonStyle estilo = new Button.ButtonStyle();
        if (caminho != null && ehMagia(caminho)) {
            // Magia: laranja no lugar dos cinzas (igual o slot da hotbar).
            Color[] c = coresSlotMagia(caminho);
            estilo.up = UiSkin.retangulo(c[0], selecionado ? c[4] : c[1], 2);
            estilo.over = aoClicar != null && !selecionado ? UiSkin.retangulo(c[2], c[3], 2) : estilo.up;
        } else {
            estilo.up = skinDrawable(selecionado ? "bag-slot-selected" : "bag-slot");
            estilo.over = aoClicar != null && !selecionado ? skinDrawable("bag-slot-hover") : estilo.up;
        }
        Button botao = new Button(estilo);
        Stack pilha = new Stack();
        if (caminho != null && !caminho.isEmpty()) {
            int qtd = ehMagia(caminho) ? 0 : quantidadeNaBag(caminho);
            Table centro = new Table();
            TextureAtlas.AtlasRegion icone = iconeDoItem(caminho);
            if (icone != null) {
                Image img = new Image(icone);
                img.setScaling(Scaling.fit);
                if (apagarIndisponivel && !disponivelNoAtalho(caminho)) img.setColor(1f, 1f, 1f, 0.3f);
                centro.add(img).size(tamanho * 0.86f);
            }
            pilha.add(centro);
            if (qtd > 0) {
                Label numero = rotuloQuantidade(qtd);
                Table canto = new Table();
                canto.bottom().right();
                canto.add(numero).padBottom(numero.getStyle().font.getDescent());
                pilha.add(canto);
            }
            if (ehMagia(caminho) && mostrarMana) {
                Table cantoMana = new Table();
                cantoMana.bottom().left();
                cantoMana.add(rotuloManaMagia(caminho, TAM_SLOT_ATALHO * 0.24f)).pad(0, 2, 1, 0);
                pilha.add(cantoMana);
            }
            if (temCooldown(caminho)) pilha.add(sobreposicaoCooldown(caminho));
        }
        if (tecla != null) {
            Label numeroTecla = new LabelPixel(tecla, skin.get("hud", Label.LabelStyle.class));
            escalarFonte(numeroTecla, 0.5f);
            numeroTecla.setColor(1f, 1f, 1f, 0.5f);
            Table cantoTecla = new Table();
            cantoTecla.top().left();
            cantoTecla.add(numeroTecla).pad(1, 4, 0, 0);
            pilha.add(cantoTecla);
        }
        botao.add(pilha).grow();
        if (aoClicar != null) {
            botao.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) { aoClicar.run(); }
            });
        } else {
            botao.setDisabled(true);
        }
        return botao;
    }

    public void atualizarMoedas(long total) {
        currencyTotal = Math.max(0, total);
        long platinum = currencyTotal / 1_000_000L;
        long remainder = currencyTotal % 1_000_000L;
        long gold = remainder / 10_000L;
        remainder %= 10_000L;
        long silver = remainder / 100L;
        long copper = remainder % 100L;
        currencyLabels.get("Copper").setText(String.valueOf(copper));
        currencyLabels.get("Silver").setText(String.valueOf(silver));
        currencyLabels.get("Gold").setText(String.valueOf(gold));
        currencyLabels.get("Platinum").setText(String.valueOf(platinum));
        if (modoTrade && !tradeEnviado) {
            // Saldo mudou: tira moeda que nao cabe mais.
            tradeSelecao.keySet().removeIf(k -> indiceMoeda(k) >= 0);
            atualizarGradeTrade();
            atualizarDetalhesTrade();
        }
        atualizarTelaBanco(); atualizarTelaLoja();
    }

    private static String formatarPeso(float valor) {
        return valor == Math.floor(valor) ? String.valueOf((int) valor)
            : String.format(java.util.Locale.US, "%.1f", valor);
    }

    public void atualizarCapacidade(float usada, float maxima) {
        capacityUsed = usada;
        capacityMaximum = maxima;
        capacityLabel.setText(formatarPeso(usada) + "/" + formatarPeso(maxima));
        if (barraCapBag != null) barraCapBag.fracao = maxima > 0f ? usada / maxima : 0f;
        atualizarTextoCapacidade();
        atualizarTelaBanco(); atualizarTelaLoja();
    }

    private static final class InventoryItem {
        final String instanceId;
        final String itemPath;
        final int quantity;
        final boolean favorite;

        InventoryItem(String instanceId, String itemPath, int quantity, boolean favorite) {
            this.instanceId = instanceId;
            this.itemPath = itemPath;
            this.quantity = quantity;
            this.favorite = favorite;
        }
    }

    private boolean atualizandoSecao = false;

    private void selecionarSecao(String secao) {
        if (atualizandoSecao) return;
        if (secaoTravada(secao)) return;
        atualizandoSecao = true;
        try {
            secaoAtual = secao;
            tituloSecao.setText(secao);
            boolean comTitulo = !"Bag".equals(secao) && !"Skills".equals(secao) && !"Equip".equals(secao)
                && !"Vanity".equals(secao) && !"Spells".equals(secao);
            tituloSecao.setVisible(comTitulo);
            if (!"Bag".equals(secao)) cancelarExclusao();
            mainWindow.clearChildren();
            // Equip ocupa o painel inteiro (colunas encostam na borda).
            mainWindow.pad("Equip".equals(secao) || "Bag".equals(secao) || "Vanity".equals(secao) ? 1
                : "Spells".equals(secao) ? 8 : 14);
            if (comTitulo) {
                // Friends: titulo centralizado (igual o mock "tela desejada 2").
                tituloSecao.setAlignment("Friends".equals(secao) || "Party".equals(secao) ? Align.center : Align.left);
                mainWindow.add(tituloSecao).growX().left().padBottom(9).row();
            }
            if ("Spells".equals(secao)) { slotAtalhoEscolhendo = -1; construirPaginaSpells(); }
            Table pagina = "Equip".equals(secao) ? equipPage : "Skills".equals(secao) ? skillsPage
                : "Spells".equals(secao) ? spellsPage
                : "Vanity".equals(secao) ? vanityPage : "Friends".equals(secao) ? friendsPage
                : "Party".equals(secao) ? partyPage
                : "Bestiary".equals(secao) ? bestiaryPage
                : "Rank".equals(secao) ? rankPage : bagPage;
            if ("Rank".equals(secao)) {
                if (rankPage.getChildren().size == 0) construirPaginaRank();
                pedirRanking();
            }
            if ("Bestiary".equals(secao)) {
                reconstruirBestiario();
                if (socket.isConnected()) socket.emitRaw("get_bestiary", "{}");
            }
            if ("Friends".equals(secao) && socket.isConnected()) socket.emitRaw("get_friends_list", "{}");
            if ("Party".equals(secao) && socket.isConnected()) socket.emitRaw("get_party_status", "{}");
            mainWindow.add(pagina).grow();
            actionBar.setVisible("Bag".equals(secao));
            for (Map.Entry<String, Button> entrada : botoes.entrySet()) {
                entrada.getValue().setChecked(entrada.getKey().equals(secao));
            }
        } finally {
            atualizandoSecao = false;
        }
    }

    private Color corDaSecao(String secao) {
        switch (secao) {
            case "Exit": return Color.valueOf("e53935");
            case "Equip": return Color.valueOf("aeb4bb");
            case "Bag": return Color.valueOf("f28c28");
            case "Skills": return Color.valueOf("f062a6");
            case "Vanity": return Color.valueOf("f2cf32");
            case "Spells": return Color.valueOf("9b59d0");
            case "Party": return Color.valueOf("c49a6c");
            case "Friends": return Color.valueOf("c2185b");
            case "Map": return Color.valueOf("42a85a");
            case "Rank": return Color.valueOf("f5d328");
            case "Bestiary": return Color.valueOf("f0a028"); // dourado alaranjado
            default: return Color.WHITE;
        }
    }

    // Atalhos do teclado com o menu aberto: seguem a ordem dos botoes na tela
    // (coluna direita de cima pra baixo, depois a esquerda). 1 = Equip,
    // 2 = Bag, 3 = Skills, ... 8 = Rank, 9 = Bestiary, 0 = Exit (o Map saiu do livro: virou
    // o minimapa, botao do retrato no WorldScreen) (Esc tambem fecha, ver WorldScreen).
    private static final String[] ORDEM_ATALHOS = {
        "Equip", "Bag", "Skills", "Vanity", "Spells", "Party", "Friends", "Rank", "Bestiary", "Exit"
    };
    private static final int[][] TECLAS_ATALHOS = {
        {com.badlogic.gdx.Input.Keys.NUM_1, com.badlogic.gdx.Input.Keys.NUMPAD_1},
        {com.badlogic.gdx.Input.Keys.NUM_2, com.badlogic.gdx.Input.Keys.NUMPAD_2},
        {com.badlogic.gdx.Input.Keys.NUM_3, com.badlogic.gdx.Input.Keys.NUMPAD_3},
        {com.badlogic.gdx.Input.Keys.NUM_4, com.badlogic.gdx.Input.Keys.NUMPAD_4},
        {com.badlogic.gdx.Input.Keys.NUM_5, com.badlogic.gdx.Input.Keys.NUMPAD_5},
        {com.badlogic.gdx.Input.Keys.NUM_6, com.badlogic.gdx.Input.Keys.NUMPAD_6},
        {com.badlogic.gdx.Input.Keys.NUM_7, com.badlogic.gdx.Input.Keys.NUMPAD_7},
        {com.badlogic.gdx.Input.Keys.NUM_8, com.badlogic.gdx.Input.Keys.NUMPAD_8},
        {com.badlogic.gdx.Input.Keys.NUM_9, com.badlogic.gdx.Input.Keys.NUMPAD_9},
        {com.badlogic.gdx.Input.Keys.NUM_0, com.badlogic.gdx.Input.Keys.NUMPAD_0},
    };

    /**
     * Listener de CAPTURA no Stage: recebe a tecla antes de qualquer ator
     * (mesmo com outro ator com foco de teclado), entao nao depende da ordem
     * dos InputProcessors do WorldScreen. Ignora enquanto se digita num
     * TextField visivel (ex: chat). Cada tecla = clicar no botao.
     */
    private void registrarAtalhos(Stage stage) {
        stage.addCaptureListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
            @Override
            public boolean keyDown(com.badlogic.gdx.scenes.scene2d.InputEvent event, int keycode) {
                if (!isVisible() || modoTrade || modoBanco || modoLoja || digitandoEmCampo(stage)) return false;
                for (int i = 0; i < ORDEM_ATALHOS.length; i++) {
                    for (int tecla : TECLAS_ATALHOS[i]) {
                        if (tecla == keycode) {
                            acionarBotao(ORDEM_ATALHOS[i]);
                            event.stop();
                            return true;
                        }
                    }
                }
                return false;
            }
        });
    }

    private static boolean digitandoEmCampo(Stage stage) {
        com.badlogic.gdx.scenes.scene2d.Actor foco = stage.getKeyboardFocus();
        if (!(foco instanceof com.badlogic.gdx.scenes.scene2d.ui.TextField)) return false;
        for (com.badlogic.gdx.scenes.scene2d.Actor a = foco; a != null; a = a.getParent()) {
            if (!a.isVisible()) return false;
        }
        return foco.getStage() != null;
    }

    /** Alma (antes do portal do Kharon): skins (Vanity), amigos e party travados e cinza. */
    private static final java.util.Set<String> SECOES_TRAVADAS_ALMA =
        new java.util.HashSet<>(java.util.Arrays.asList("Vanity", "Friends", "Party"));
    private boolean skinsBloqueadas = false;

    public void definirAlma(boolean alma) {
        skinsBloqueadas = alma;
        for (String secao : SECOES_TRAVADAS_ALMA) {
            Button b = botoes.get(secao);
            if (b == null) continue;
            b.setDisabled(alma);
            b.setColor(alma ? new Color(0.4f, 0.4f, 0.4f, 1f) : Color.WHITE);
        }
        if (alma && SECOES_TRAVADAS_ALMA.contains(secaoAtual) && root.isVisible()) selecionarSecao("Equip");
    }

    private boolean secaoTravada(String secao) { return skinsBloqueadas && SECOES_TRAVADAS_ALMA.contains(secao); }

    private void acionarBotao(String nome) {
        if (secaoTravada(nome)) return;
        if ("Exit".equals(nome)) {
            Button exit = botoes.get("Exit");
            if (exit != null) exit.setChecked(false);
            setVisible(false);
        } else {
            selecionarSecao(nome);
        }
    }

    public void alternar() {
        setVisible(!root.isVisible());
    }

    public void setVisible(boolean visible) {
        if (!visible && modoTrade) {
            cancelarTrade(); // fechar o livro no meio do trade cancela pros dois
            return;
        }
        if (!visible && modoBanco) {
            fecharBanco();
            return;
        }
        if (!visible && modoLoja) {
            fecharLoja();
            return;
        }
        if (!visible && modoMercado) {
            fecharMercado();
            return;
        }
        if (visible && modoLoja && !root.isVisible()) fecharLoja();
        if (visible && modoBanco && !root.isVisible()) {
            fecharBanco(); // abrir o livro normal (tecla) sai do bank
        }
        if (!visible) cancelarExclusao();
        // Abrindo de novo ja' no Bestiary: pede os numeros atualizados.
        if (visible && !root.isVisible() && "Bestiary".equals(secaoAtual) && socket.isConnected()) {
            socket.emitRaw("get_bestiary", "{}");
        }
        root.setVisible(visible);
    }

    public boolean isVisible() {
        return root.isVisible();
    }
}