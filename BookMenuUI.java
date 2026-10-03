package com.teste.game.telas;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
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
    private final Table vanityPage = new Table();
    private final Table inventoryGrid = new Table();
    private final Table bagDetalhes = new Table();
    private final Label capacityLabel;
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
    private final List<Integer> equipCandidatos = new ArrayList<>();
    private final Table equipItemGrid = new Table();
    private final Table equipDetalhesEsquerda = new Table();
    private final Table equipDetalhesDireita = new Table();
    private Table equipColunaEsquerda;
    private Table equipSlotsTabela;
    private Table equipColunaDireita;
    private Table equipPainelGrade;
    private TextButton equipButton;
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
        Table leftTab = criarColuna(atlas, new String[][]{
            {"Party", "ui/buttons/PartyBtn"},
            {"Friends", "ui/buttons/FriendsBtn"},
            {"Map", "ui/buttons/MapBtn"},
            {"Rank", "ui/buttons/RankBtn"},
            {"Exit", "ui/buttons/CloseBtn"}
        }, true);
        Table rightTab = criarColuna(atlas, new String[][]{
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
        tituloSecao = new Label("Bag", skin, "subtitulo");
        capacityLabel = new Label("Capacity 0/0", skin, "hud");
        actionStatus = new Label("", skin, "hud");
        construirPaginaBag(skin);
        construirPaginaEquip(skin);
        atualizarEquipados(null);
        construirPaginaSkills(skin);
        construirPaginaVanity(skin);
        mainWindow.add(bagPage).grow();

        organizer.add(leftTab).width(COLUNA_LARGURA).height(JANELA_ALTURA);
        organizer.add(mainWindow).width(JANELA_LARGURA).height(JANELA_ALTURA);
        organizer.add(rightTab).width(COLUNA_LARGURA).height(JANELA_ALTURA);
        organizer.setTransform(true);
        boolean mobile = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android
            || Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.iOS;
        organizer.setScale(mobile ? 1.2f : 1f);

        root.setFillParent(true);
        root.center();
        root.add(organizer).width(COLUNA_LARGURA * 2f + JANELA_LARGURA)
            .height(JANELA_ALTURA);
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

        actionStatus.setFontScale(0.6f);
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

        capacityLabel.setFontScale(0.8f);
        capacityLabel.setColor(new Color(0.65f, 0.65f, 0.65f, 1f));

        Table moedas = new Table();
        moedas.left();
        String[] tiposMoeda = {"Copper", "Silver", "Gold", "Platinum"};
        for (String tipo : tiposMoeda) {
            Image icone = new Image(new TextureRegionDrawable(atlas.findRegion("ui/currency/" + tipo)));
            icone.setScaling(Scaling.fit);
            Label quantidade = new Label("0", skin, "hud");
            quantidade.setFontScale(0.7f);
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
        colunaDireita.add(capacityLabel).center().padBottom(8).row();
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
        final int bonusDamage, defense, stamina, mana, fourthStatValue;
        final String fourthStatType;

        EquipStats(JsonValue d) {
            this.nome = d.getString("name", "");
            this.tipo = d.getString("type", "");
            this.slot = d.getString("slot", null);
            this.reqLevel = d.getInt("req_level", 0);
            this.reqClass = d.getString("req_class", "All");
            this.bonusDamage = d.getInt("bonus_damage", 0);
            this.defense = d.getInt("defense", 0);
            this.stamina = d.getInt("stamina", 0);
            this.mana = d.getInt("mana", 0);
            this.fourthStatType = d.getString("fourth_stat_type", "");
            this.fourthStatValue = d.getInt("fourth_stat_value", 0);
        }
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
        estiloEquipar.font = skin.getFont("botao-pequeno-font");
        TextButton.TextButtonStyle estiloDesequipar = new TextButton.TextButtonStyle(
            skin.get("default", TextButton.TextButtonStyle.class));
        estiloDesequipar.font = skin.getFont("botao-pequeno-font");
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
    }

    private void atualizarGradeEquip() {
        equipCandidatos.clear();
        for (int i = 0; i < inventoryItems.size(); i++) {
            String caminho = inventoryItems.get(i).itemPath;
            String slotItem = slotDoItem(caminho);
            if (slotItem == null && !ITEM_STATS.containsKey(caminho)) continue;
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
            if (textura != null) {
                Image icone = new Image(new TextureRegionDrawable(textura));
                icone.setScaling(Scaling.fit);
                slotBtn.add(icone).grow().pad(6);
            }
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
            preencherBlocoStats(equipDetalhesEsquerda, equipadoPath, null);
            equipColunaEsquerda.add(separadorEquip()).growX().height(1).padTop(4).row();
            equipColunaEsquerda.add(equipDetalhesEsquerda).growX().top().left().pad(10, 12, 10, 12).row();
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
            equipColunaDireita.add(equipDetalhesDireita).grow().top().left().pad(10, 12, 10, 12);
            equipButton.setDisabled(slotAlvo == null && selectedSlot.isEmpty());
        } else {
            equipColunaDireita.add(equipPainelGrade).grow().row();
            if (equipadoPath != null) {
                equipColunaDireita.add(unequipButton).width(120).height(42).right().padRight(14).padBottom(10);
            }
        }
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
        bloco.clearChildren();
        bloco.top().left();
        // Fonte fina ("hud", sem contorno) reduzida; linhas na altura natural
        // da fonte (sem sobrepor) e sem espaco extra entre elas.
        Label nome = new Label(nomeExibicao(itemPath), skin, "hud");
        nome.setFontScale(0.74f);
        nome.setWrap(true);
        bloco.add(nome).growX().left().padBottom(1).row();
        EquipStats dados = ITEM_STATS.get(itemPath);
        if (dados == null) return;
        EquipStats base = compararCom == null ? null : ITEM_STATS.get(compararCom);
        boolean comparar = compararCom != null;
        bloco.add(linhaStat("Req. Lv " + dados.reqLevel, COR_REQUISITO)).left().row();
        // Vermelho = classe errada (o servidor pune quem equipa item de outra classe).
        boolean classeCerta = "All".equals(dados.reqClass) || classeJogador.equals(dados.reqClass);
        bloco.add(linhaStat(dados.reqClass + " " + dados.tipo,
            classeCerta ? corClasse(dados.reqClass) : Color.valueOf("ff4a4a"))).left().row();
        adicionarLinhaStat(bloco, "Attack", dados.bonusDamage, base == null ? 0 : base.bonusDamage, comparar, Color.WHITE);
        adicionarLinhaStat(bloco, "Defense", dados.defense, base == null ? 0 : base.defense, comparar, Color.WHITE);
        adicionarLinhaStat(bloco, "Stamina", dados.stamina, base == null ? 0 : base.stamina, comparar, COR_STAMINA);
        adicionarLinhaStat(bloco, "Mana", dados.mana, base == null ? 0 : base.mana, comparar, COR_MANA);
        if (dados.fourthStatType != null && !dados.fourthStatType.isEmpty() && !"None".equals(dados.fourthStatType)) {
            int anterior = base != null && dados.fourthStatType.equals(base.fourthStatType) ? base.fourthStatValue : 0;
            adicionarLinhaStat(bloco, dados.fourthStatType, dados.fourthStatValue, anterior, comparar,
                corQuartoStat(dados.fourthStatType));
        }
    }

    private void adicionarLinhaStat(Table bloco, String nome, int valor, int anterior, boolean comparar, Color cor) {
        if (valor == 0 && (!comparar || anterior == 0)) return;
        String texto = nome + " " + valor;
        if (comparar) {
            int diferenca = valor - anterior;
            texto += " (" + (diferenca >= 0 ? "+" : "") + diferenca + ")";
        }
        bloco.add(linhaStat(texto, cor)).left().row();
    }

    private Label linhaStat(String texto, Color cor) {
        Label label = new Label(texto, skin, "hud");
        label.setFontScale(0.62f);
        label.setColor(cor);
        return label;
    }

    private void equiparSelecionado() {
        if (selectedEquipCandidate < 0 || selectedEquipCandidate >= inventoryItems.size()
            || !socket.isConnected()) return;
        InventoryItem item = inventoryItems.get(selectedEquipCandidate);
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

    public void atualizarEquipados(JsonValue dados) {
        equippedItemPaths.clear();
        if (dados != null && dados.isObject()) {
            for (JsonValue entrada = dados.child; entrada != null; entrada = entrada.next) {
                String caminho = entrada.getString("item", "");
                if (!caminho.isEmpty()) equippedItemPaths.put(entrada.name, caminho);
            }
        }
        for (Map.Entry<String, Button> entrada : equipSlotButtons.entrySet()) {
            Button botao = entrada.getValue();
            botao.clearChildren();
            String caminho = equippedItemPaths.get(entrada.getKey());
            TextureAtlas.AtlasRegion textura = caminho == null ? null : iconeDoItem(caminho);
            if (textura == null) textura = atlas.findRegion(regiaoSlotVazio(entrada.getKey()));
            if (textura != null) {
                Image icone = new Image(new TextureRegionDrawable(textura));
                icone.setScaling(Scaling.fit);
                botao.add(icone).grow().pad(caminho == null ? 4 : 6);
            }
        }
        atualizarDetalhesEquip();
        // A comparacao (+x) dos detalhes da Bag depende do que esta equipado.
        if (favoriteButton != null) atualizarDetalhes(itemSelecionado());
    }

    // ===================== SKILLS =====================

    private String[] skillPrincipal() {
        switch (classeJogador) {
            case "Ranger": return new String[]{"distance", "Distance"};
            case "Mage": return new String[]{"magic", "Magic"};
            case "Bard": return new String[]{"musicality", "Musicality"};
            default: return new String[]{"melee", "Melee"};
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
        Label nomeLabel = new Label(nome, skin, "hud");
        nomeLabel.setColor(corTexto);
        Label nivelLabel = null;
        pai.add(nomeLabel).left().width(LARGURA_NOME_SKILL);
        if (comNivel) {
            nivelLabel = new Label("1", skin, "hud");
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
        Label percentLabel = new Label("0%", skin, "hud");
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
        statusEsquerda.add(linhaStatus("ui/BlockIcon", "Block Chance", "block")).left().row();
        statusEsquerda.add(linhaStatus("ui/currency/BagIcon", "Capacity", "capacity")).left().row();

        Table statusDireita = new Table();
        statusDireita.top().left();
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
        float pct = Math.max(0f, Math.min(1f, percentual));
        if (barra.nivelLabel != null) barra.nivelLabel.setText(String.valueOf(nivel));
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
     * fullness/hunger (0-100), crit_chance, block_chance e total_exp.
     */
    public void atualizarSkills(JsonValue skills, int level, int exp, int kills) {
        this.ultimasSkills = skills;
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
        atualizarBarra(barraPrincipal, nivelPrincipal, pctPrincipal);

        int nivelDefesa = skills != null ? skills.getInt("defense", 10) : 10;
        int hitsDefesa = skills != null ? skills.getInt("defense_hits", 0) : 0;
        int necessarioDefesa = getHitsToLevel(nivelDefesa + 1, getSkillMultiplier(classeJogador, true));
        float pctDefesa = necessarioDefesa > 0 ? (float) hitsDefesa / necessarioDefesa : 0f;
        atualizarBarra(barraDefesa, nivelDefesa, pctDefesa);

        float fullness = skills != null ? skills.getFloat("fullness", skills.getFloat("hunger", 0f)) : 0f;
        atualizarBarra(barraFome, 0, fullness / 100f);

        float crit = skills != null ? skills.getFloat("crit_chance", 10f) : 10f;
        float block = skills != null ? skills.getFloat("block_chance", 10f) : 10f;
        long totalExp = skills != null ? skills.getLong("total_exp", exp) : exp;

        valoresStatus.get("class").setText(classeJogador);
        valoresStatus.get("exp").setText(formatarNumero(exp));
        valoresStatus.get("next").setText(formatarNumero(expProximo));
        valoresStatus.get("crit").setText(formatarPercentual(crit));
        valoresStatus.get("block").setText(formatarPercentual(block));
        atualizarTextoCapacidade();
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
        Label r = new Label(rotulo, skin, "hud");
        Label v = new Label("", skin, "hud");
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
    private static final String[] CATEGORIAS_VANITY = {"base", "body", "helm", "acc"};
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
    private static final List<Color> PALETA = criarPaleta();

    private static List<Color> criarPaleta() {
        List<Color> cores = new ArrayList<>();
        // Branco -> cinza escuro
        for (int i = 0; i < 8; i++) {
            float v = 1f - i * 0.1f;
            cores.add(new Color(v, v, v, 1f));
        }
        // Tons de pele/marrom
        for (String h : new String[]{"ffe0bd", "f1c27d", "e0ac69", "c68642", "8d5524", "6b4a3e", "5c3a1e", "3b2414"}) {
            cores.add(Color.valueOf(h));
        }
        // Arco-iris (6 linhas de 8)
        for (int i = 0; i < 48; i++) {
            Color c = new Color();
            c.fromHsv(i * (360f / 48f), 0.88f, 0.94f);
            c.a = 1f;
            cores.add(c);
        }
        // Versoes escuras
        for (int i = 0; i < 8; i++) {
            Color c = new Color();
            c.fromHsv(i * 45f, 0.85f, 0.45f);
            c.a = 1f;
            cores.add(c);
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
        estiloBotao.font = skin.getFont("botao-pequeno-font");
        // Cinza sem alteracoes; verde quando o rascunho difere do que esta equipado.
        estiloAplicarNormal = estiloBotao;
        estiloAplicarAlterado = new TextButton.TextButtonStyle(skin.get("verde", TextButton.TextButtonStyle.class));
        estiloAplicarAlterado.font = skin.getFont("botao-pequeno-font");
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
            Label nome = new Label(nomeCategoriaVanity(cat), skin, "hud");
            nome.setFontScale(0.8f);
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
            case "body": return "Cloth";
            case "helm": return "Hair";
            case "acc": return "Back";
            default: return cat;
        }
    }

    private void montarOpcoesVanity() {
        vanityOpcoes.clearChildren();
        List<String[]> opcoes = new ArrayList<>();
        // Roupa/cabeca/acessorio podem ficar vazios; a pele (base) nao.
        if (!"base".equals(categoriaVanity)) opcoes.add(null);
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
                        skinsRascunho.put(categoriaVanity,
                            new String[]{opcao[0], anterior != null ? anterior[1] : "ffffffff"});
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
    private void montarPreviewVanity() {
        vanityPreview.clearChildren();
        int[] direcoes = {SkinsUtil.FRAME_BAIXO, SkinsUtil.FRAME_ESQUERDA, SkinsUtil.FRAME_DIREITA, SkinsUtil.FRAME_CIMA};
        for (int i = 0; i < direcoes.length; i++) {
            Stack pilha = new Stack();
            float altura = 17f;
            for (String cat : SkinsUtil.ORDEM_CAMADAS) {
                String[] escolhida = skinsRascunho.get(cat);
                String caminho = escolhida != null ? escolhida[0] : "base".equals(cat) ? SkinsUtil.BASE_PADRAO : null;
                TextureAtlas.AtlasRegion tira = SkinsUtil.regiao(atlas, caminho);
                if (tira == null) continue;
                Image camada = new Image(new TextureRegionDrawable(SkinsUtil.quadro(tira, direcoes[i])));
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
                    if (item.quantity > 1) {
                        Label quantidade = new Label(String.valueOf(item.quantity), skin, "hud");
                        quantidade.setFontScale(0.6f);
                        quantidade.setColor(Color.WHITE);
                        marcadores.add(quantidade).left().bottom();
                    }
                    marcadores.add().expandX();
                    if (item.favorite) {
                        Image estrela = new Image(new TextureRegionDrawable(atlas.findRegion("ui/Star")));
                        estrela.setScaling(Scaling.fit);
                        marcadores.add(estrela).size(16f).right().bottom();
                    }
                    conteudo.add(marcadores);
                }
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

    private TextureAtlas.AtlasRegion iconeDoItem(String caminhoItem) {
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
            ITEM_STATS.containsKey(item.itemPath) ? (equipado == null ? "" : equipado) : null);
        if (item.quantity > 1) bagDetalhes.add(linhaStat("Quantity " + item.quantity, Color.LIGHT_GRAY)).left().row();
        if (item.favorite) bagDetalhes.add(linhaStat("Favorite", new Color(1f, 0.82f, 0.24f, 1f))).left().row();
        favoriteButton.setDisabled(item.instanceId.isEmpty());
        trashButton.setDisabled(false);
    }

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

    private String nomeExibicao(String caminho) {
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
    }

    private static String formatarPeso(float valor) {
        return valor == Math.floor(valor) ? String.valueOf((int) valor)
            : String.format(java.util.Locale.US, "%.1f", valor);
    }

    public void atualizarCapacidade(float usada, float maxima) {
        capacityUsed = usada;
        capacityMaximum = maxima;
        capacityLabel.setText("Capacity " + formatarPeso(usada) + "/" + formatarPeso(maxima));
        atualizarTextoCapacidade();
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
        atualizandoSecao = true;
        try {
            secaoAtual = secao;
            tituloSecao.setText(secao);
            boolean comTitulo = !"Bag".equals(secao) && !"Skills".equals(secao) && !"Equip".equals(secao)
                && !"Vanity".equals(secao);
            tituloSecao.setVisible(comTitulo);
            if (!"Bag".equals(secao)) cancelarExclusao();
            mainWindow.clearChildren();
            // Equip ocupa o painel inteiro (colunas encostam na borda).
            mainWindow.pad("Equip".equals(secao) || "Bag".equals(secao) || "Vanity".equals(secao) ? 1 : 14);
            if (comTitulo) {
                mainWindow.add(tituloSecao).growX().left().padBottom(9).row();
            }
            Table pagina = "Equip".equals(secao) ? equipPage : "Skills".equals(secao) ? skillsPage
                : "Vanity".equals(secao) ? vanityPage : bagPage;
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
            default: return Color.WHITE;
        }
    }

    // Atalhos do teclado com o menu aberto: seguem a ordem dos botoes na tela
    // (coluna direita de cima pra baixo, depois a esquerda). 1 = Equip,
    // 2 = Bag, 3 = Skills, ... 9 = Rank, 0 = Exit (Esc tambem fecha, ver WorldScreen).
    private static final String[] ORDEM_ATALHOS = {
        "Equip", "Bag", "Skills", "Vanity", "Spells", "Party", "Friends", "Map", "Rank", "Exit"
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
                if (!isVisible() || digitandoEmCampo(stage)) return false;
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

    private void acionarBotao(String nome) {
        if ("Exit".equals(nome)) {
            setVisible(false);
        } else {
            selecionarSecao(nome);
        }
    }

    public void alternar() {
        setVisible(!root.isVisible());
    }

    public void setVisible(boolean visible) {
        if (!visible) cancelarExclusao();
        root.setVisible(visible);
    }

    public boolean isVisible() {
        return root.isVisible();
    }
}