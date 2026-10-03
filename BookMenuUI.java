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
    private static final int COLUNAS_INVENTARIO = 7;

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
    private final Table inventoryGrid = new Table();
    private final Label itemName;
    private final Label itemQuantity;
    private final Label itemFavorite;
    private final Label capacityLabel;
    private final Label actionStatus;
    private final Map<String, Label> currencyLabels = new LinkedHashMap<>();
    private final List<InventoryItem> inventoryItems = new ArrayList<>();
    private Button favoriteButton;
    private Button trashButton;
    private Table actionButtons;
    private Table deleteConfirmation;
    private Table actionBar;
    private boolean confirmandoExclusao = false;
    private int selectedItem = -1;
    private String selectedInstanceId = "";
    private long currencyTotal;
    private float capacityUsed;
    private float capacityMaximum;
    private String secaoAtual = "Bag";

    // --- Equip ---
    private static final String[][] SLOTS_EQUIP = {
        {"Helm", "Necklace"}, {"MainHand", "Hand"}, {"Chest", "Gloves"}, {"Boots", "Ring"}
    };
    private final Map<String, Button> equipSlotButtons = new LinkedHashMap<>();
    private final Map<String, String> equippedItemPaths = new LinkedHashMap<>();
    private final Table equipItemGrid = new Table();
    private Label equipSlotTitle;
    private Table equipEquipadoBloco;
    private Table equipCandidatoBloco;
    private Button equipButton;
    private Button unequipButton;
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
            new Color(0.025f, 0.028f, 0.03f, 1f), new Color(0.12f, 0.13f, 0.14f, 1f), 1),
            com.badlogic.gdx.scenes.scene2d.utils.Drawable.class);
        skin.add("bag-slot-hover", UiSkin.retangulo(
            new Color(0.07f, 0.08f, 0.085f, 1f), new Color(0.26f, 0.28f, 0.3f, 1f), 1),
            com.badlogic.gdx.scenes.scene2d.utils.Drawable.class);
        skin.add("bag-slot-selected", UiSkin.retangulo(
            new Color(0.17f, 0.2f, 0.2f, 1f), new Color(0.95f, 0.65f, 0.24f, 1f), 2),
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
        itemName = new Label("", skin, "default");
        itemName.setWrap(true);
        itemQuantity = new Label("", skin, "hud");
        itemFavorite = new Label("", skin, "hud");
        capacityLabel = new Label("Capacity: 0 / 0", skin, "hud");
        actionStatus = new Label("", skin, "hud");
        construirPaginaBag(skin);
        construirPaginaEquip(skin);
        construirPaginaSkills(skin);
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
                    if ("Exit".equals(nome)) {
                        setVisible(false);
                    } else {
                        selecionarSecao(nome);
                    }
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
        bagPage.defaults().top();
        inventoryGrid.top().left();

        Table detalhes = new Table();
        detalhes.setBackground(skin.getDrawable("bag-detail"));
        detalhes.top().left().pad(8);
        detalhes.add(itemName).growX().left().padBottom(10).row();
        detalhes.add(itemQuantity).growX().left().padBottom(5).row();
        detalhes.add(itemFavorite).growX().left().padBottom(5).row();
        detalhes.add(new Table()).growY();

        actionButtons = new Table();
        actionButtons.center();
        favoriteButton = criarBotaoAcao("ui/Star", () -> alternarFavorito());
        trashButton = criarBotaoAcao("ui/Trash", () -> iniciarExclusao());
        actionButtons.add(favoriteButton).size(48).padRight(5);
        actionButtons.add(trashButton).size(48);
        deleteConfirmation = new Table();
        deleteConfirmation.center();
        Button confirmar = criarBotaoAcao("ui/Confirm", () -> confirmarExclusao());
        Button cancelar = criarBotaoAcao("ui/Negate", () -> cancelarExclusao());
        deleteConfirmation.add(confirmar).size(42).padRight(5);
        deleteConfirmation.add(cancelar).size(42);
        deleteConfirmation.setVisible(false);

        actionBar = new Table();
        actionBar.center();
        actionBar.add(actionStatus).growX().center().height(14).padBottom(3).row();
        actionBar.add(actionButtons).center().height(48).row();
        actionBar.add(deleteConfirmation).center().height(42);

        Table painelGrade = new Table();
        painelGrade.pad(7);
        ScrollPane scroll = new ScrollPane(inventoryGrid, skin);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        scroll.setOverscroll(false, false);
        scroll.setFlickScroll(true);
        painelGrade.add(scroll).grow();

        Table colunaDireita = new Table();
        colunaDireita.add(painelGrade).grow().row();
        capacityLabel.setText("Capacity: 0 / 100");
        capacityLabel.setColor(new Color(0.72f, 0.72f, 0.72f, 1f));
        colunaDireita.add(capacityLabel).center().padTop(5).padBottom(5).row();

        Table moedas = new Table();
        moedas.pad(5, 4, 5, 4);
        String[] tiposMoeda = {"Copper", "Silver", "Gold", "Platinum"};
        for (String tipo : tiposMoeda) {
            Table moeda = new Table();
            Image icone = new Image(new TextureRegionDrawable(atlas.findRegion("ui/currency/" + tipo)));
            icone.setScaling(Scaling.fit);
            Label quantidade = new Label("0", skin, "hud");
            quantidade.setColor(corMoeda(tipo));
            moeda.add(icone).size(18).padRight(2);
            moeda.add(quantidade).minWidth(28).left();
            moedas.add(moeda).expandX().center();
            currencyLabels.put(tipo, quantidade);
        }
        colunaDireita.add(moedas).growX().height(32);

        Table colunaEsquerda = new Table();
        colunaEsquerda.top();
        colunaEsquerda.add(detalhes).growX().growY().row();
        colunaEsquerda.add(actionBar).growX().height(68).padTop(6);

        bagPage.add(colunaEsquerda).width(138).growY().padTop(7).padRight(7);
        bagPage.add(colunaDireita).grow();
        atualizarDetalhes(null);
        atualizarGrade();
    }

    // ===================== EQUIP =====================

    private static final class EquipStats {
        final String reqClass;
        final int bonusDamage, defense, stamina, mana, fourthStatValue;
        final String fourthStatType;

        EquipStats(String reqClass, int bonusDamage, int defense, int stamina, int mana,
                   String fourthStatType, int fourthStatValue) {
            this.reqClass = reqClass;
            this.bonusDamage = bonusDamage;
            this.defense = defense;
            this.stamina = stamina;
            this.mana = mana;
            this.fourthStatType = fourthStatType;
            this.fourthStatValue = fourthStatValue;
        }
    }

    private static final Map<String, EquipStats> ITEM_STATS = new LinkedHashMap<>();
    static {
        ITEM_STATS.put("res://sprites/items/Sword.tres", new EquipStats("Knight", 100, 0, 0, 0, "None", 0));
        ITEM_STATS.put("res://sprites/items/Bard/Weapons/StarterFlute.tres", new EquipStats("Bard", 1, 0, 5, 5, "Musicality", 3));
        ITEM_STATS.put("res://sprites/items/Bard/SecondHand/StarterSheet.tres", new EquipStats("Bard", 0, 0, 10, 10, "Musicality", 3));
        ITEM_STATS.put("res://sprites/items/Knight/Weapons/StarterSword.tres", new EquipStats("Knight", 1, 5, 10, 0, "Melee", 5));
        ITEM_STATS.put("res://sprites/items/Knight/SecondHand/StarterShield.tres", new EquipStats("Knight", 1, 5, 10, 0, "", 0));
        ITEM_STATS.put("res://sprites/items/Mage/Weapons/StarterStaff.tres", new EquipStats("Mage", 1, 0, 0, 10, "Magic", 2));
        ITEM_STATS.put("res://sprites/items/Mage/SecondHand/StarterBook.tres", new EquipStats("Mage", 0, 0, 0, 10, "Magic", 5));
        ITEM_STATS.put("res://sprites/items/Ranger/Weapons/StarterBow.tres", new EquipStats("Ranger", 1, 0, 5, 0, "Focus", 5));
        ITEM_STATS.put("res://sprites/items/Ranger/SecondHand/StarterArrow.tres", new EquipStats("Ranger", 0, 0, 0, 0, "Focus", 5));
    }

    private void construirPaginaEquip(Skin skin) {
        equipPage.top().left();
        equipItemGrid.top().left();

        Table slots = new Table();
        slots.top();
        for (String[] linha : SLOTS_EQUIP) {
            for (String nomeSlot : linha) {
                Button.ButtonStyle estilo = new Button.ButtonStyle();
                estilo.up = skin.getDrawable("bag-slot");
                estilo.over = skin.getDrawable("bag-slot-hover");
                estilo.down = skin.getDrawable("bag-slot-selected");
                estilo.checked = skin.getDrawable("bag-slot-selected");
                estilo.checkedOver = estilo.checked;
                Button botao = new Button(estilo);
                botao.addListener(new ChangeListener() {
                    @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                        selecionarSlotEquip(nomeSlot);
                    }
                });
                equipSlotButtons.put(nomeSlot, botao);

                Table celula = new Table();
                Label rotulo = new Label(nomeSlot, skin, "hud");
                rotulo.setFontScale(0.5f);
                rotulo.setAlignment(Align.center);
                celula.add(rotulo).growX().center().padBottom(2).row();
                celula.add(botao).size(42);
                slots.add(celula).pad(2);
            }
            slots.row();
        }

        Table detalhes = new Table();
        detalhes.setBackground(skin.getDrawable("bag-detail"));
        detalhes.top().left().pad(8);
        equipSlotTitle = new Label("Select a slot", skin, "default");
        equipSlotTitle.setWrap(true);
        Label rotuloEquipado = new Label("Equipped", skin, "hud");
        rotuloEquipado.setColor(Color.GRAY);
        rotuloEquipado.setFontScale(0.85f);
        equipEquipadoBloco = new Table();
        Label rotuloCandidato = new Label("Candidate", skin, "hud");
        rotuloCandidato.setColor(Color.GRAY);
        rotuloCandidato.setFontScale(0.85f);
        equipCandidatoBloco = new Table();
        detalhes.add(equipSlotTitle).growX().left().padBottom(6).row();
        detalhes.add(rotuloEquipado).left().row();
        detalhes.add(equipEquipadoBloco).growX().left().padBottom(8).row();
        detalhes.add(rotuloCandidato).left().row();
        detalhes.add(equipCandidatoBloco).growX().left().row();
        detalhes.add(new Table()).growY();

        Label.LabelStyle fontePequena = skin.get("hud", Label.LabelStyle.class);
        TextButton.TextButtonStyle estiloEquipar = new TextButton.TextButtonStyle(
            skin.get("verde", TextButton.TextButtonStyle.class));
        estiloEquipar.font = fontePequena.font;
        TextButton.TextButtonStyle estiloDesequipar = new TextButton.TextButtonStyle(
            skin.get("vermelho", TextButton.TextButtonStyle.class));
        estiloDesequipar.font = fontePequena.font;
        TextButton botaoEquipar = new TextButton("Equip", estiloEquipar);
        botaoEquipar.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                equiparSelecionado();
            }
        });
        TextButton botaoDesequipar = new TextButton("Unequip", estiloDesequipar);
        botaoDesequipar.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                desequiparSlotSelecionado();
            }
        });
        equipButton = botaoEquipar;
        unequipButton = botaoDesequipar;

        Table botoesAcao = new Table();
        botoesAcao.center();
        botoesAcao.add(equipButton).width(62).height(26).padRight(4);
        botoesAcao.add(unequipButton).width(62).height(26);

        Table colunaEsquerda = new Table();
        colunaEsquerda.top();
        colunaEsquerda.add(slots).padBottom(6).row();
        colunaEsquerda.add(botoesAcao).padTop(4);

        Table painelGrade = new Table();
        painelGrade.pad(7);
        ScrollPane scroll = new ScrollPane(equipItemGrid, skin);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        scroll.setOverscroll(false, false);
        scroll.setFlickScroll(true);
        painelGrade.add(scroll).grow();

        Table colunaDireita = new Table();
        colunaDireita.add(detalhes).growX().height(172).padBottom(6).row();
        colunaDireita.add(painelGrade).grow();

        equipPage.add(colunaEsquerda).width(138).growY().padTop(7).padRight(7);
        equipPage.add(colunaDireita).grow();
        atualizarDetalhesEquip();
        atualizarGradeEquip();
    }

    private void selecionarSlotEquip(String slot) {
        selectedSlot = slot;
        selectedEquipCandidate = -1;
        for (Map.Entry<String, Button> entrada : equipSlotButtons.entrySet()) {
            entrada.getValue().setChecked(entrada.getKey().equals(slot));
        }
        atualizarDetalhesEquip();
    }

    private void atualizarGradeEquip() {
        equipItemGrid.clearChildren();
        for (int i = 0; i < inventoryItems.size(); i++) {
            final int indice = i;
            InventoryItem item = inventoryItems.get(i);
            Button.ButtonStyle estilo = new Button.ButtonStyle();
            estilo.up = skinDrawable("bag-slot");
            estilo.over = skinDrawable("bag-slot-hover");
            estilo.down = skinDrawable("bag-slot-selected");
            estilo.checked = skinDrawable("bag-slot-selected");
            estilo.checkedOver = estilo.checked;
            Button slotBtn = new Button(estilo);
            slotBtn.setChecked(i == selectedEquipCandidate);
            TextureAtlas.AtlasRegion textura = iconeDoItem(item.itemPath);
            if (textura != null) {
                Image icone = new Image(new TextureRegionDrawable(textura));
                icone.setScaling(Scaling.fit);
                slotBtn.add(icone).grow().pad(4);
            }
            slotBtn.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                    selectedEquipCandidate = (selectedEquipCandidate == indice) ? -1 : indice;
                    atualizarDetalhesEquip();
                    atualizarGradeEquip();
                }
            });
            equipItemGrid.add(slotBtn).size(42).pad(1);
            if ((i + 1) % COLUNAS_INVENTARIO == 0) equipItemGrid.row();
        }
    }

    private void atualizarDetalhesEquip() {
        if (selectedSlot.isEmpty()) {
            equipSlotTitle.setText("Select a slot");
            preencherBlocoStats(equipEquipadoBloco, null);
            preencherBlocoStats(equipCandidatoBloco, null);
            equipButton.setDisabled(true);
            unequipButton.setDisabled(true);
            return;
        }
        equipSlotTitle.setText(selectedSlot);
        String equipadoPath = equippedItemPaths.get(selectedSlot);
        preencherBlocoStats(equipEquipadoBloco, equipadoPath);
        unequipButton.setDisabled(equipadoPath == null);

        if (selectedEquipCandidate >= 0 && selectedEquipCandidate < inventoryItems.size()) {
            String candidatoPath = inventoryItems.get(selectedEquipCandidate).itemPath;
            preencherBlocoStats(equipCandidatoBloco, candidatoPath);
            equipButton.setDisabled(false);
        } else {
            preencherBlocoStats(equipCandidatoBloco, null);
            equipButton.setDisabled(true);
        }
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
            case "Musicality": return Color.valueOf("ffa24e");
            case "Melee": return Color.valueOf("cccccc");
            default: return Color.WHITE;
        }
    }

    private void preencherBlocoStats(Table bloco, String itemPath) {
        bloco.clearChildren();
        bloco.top().left();
        if (itemPath == null) {
            Label vazio = linhaStat("(empty)", Color.GRAY);
            bloco.add(vazio).left();
            return;
        }
        Label nome = linhaStat(nomeExibicao(itemPath), Color.WHITE);
        bloco.add(nome).left().row();
        EquipStats dados = ITEM_STATS.get(itemPath);
        if (dados == null) return;
        bloco.add(linhaStat(dados.reqClass, corClasse(dados.reqClass))).left().row();
        if (dados.bonusDamage != 0) bloco.add(linhaStat("Attack +" + dados.bonusDamage, Color.WHITE)).left().row();
        if (dados.defense != 0) bloco.add(linhaStat("Defense +" + dados.defense, Color.WHITE)).left().row();
        if (dados.stamina != 0) bloco.add(linhaStat("Stamina +" + dados.stamina, Color.valueOf("6ab7ff"))).left().row();
        if (dados.mana != 0) bloco.add(linhaStat("Mana +" + dados.mana, Color.valueOf("4287f5"))).left().row();
        if (dados.fourthStatType != null && !dados.fourthStatType.isEmpty()
            && !"None".equals(dados.fourthStatType) && dados.fourthStatValue != 0) {
            bloco.add(linhaStat(dados.fourthStatType + " +" + dados.fourthStatValue,
                corQuartoStat(dados.fourthStatType))).left().row();
        }
    }

    private Label linhaStat(String texto, Color cor) {
        Label label = new Label(texto, skin, "hud");
        label.setFontScale(0.85f);
        label.setColor(cor);
        return label;
    }

    private void equiparSelecionado() {
        if (selectedSlot.isEmpty() || selectedEquipCandidate < 0 || selectedEquipCandidate >= inventoryItems.size()
            || !socket.isConnected()) return;
        String instanceId = inventoryItems.get(selectedEquipCandidate).instanceId;
        if (instanceId.isEmpty()) return;
        String slot = selectedSlot;
        String payload = GameSocket.obj(w -> { w.set("instance_id", instanceId); w.set("slot", slot); });
        socket.emitRaw("equip_item", payload);
        selectedEquipCandidate = -1;
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
            if (textura != null) {
                Image icone = new Image(new TextureRegionDrawable(textura));
                icone.setScaling(Scaling.fit);
                botao.add(icone).grow().pad(4);
            }
        }
        atualizarDetalhesEquip();
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
        pai.add(pilha).width(LARGURA_BARRA_SKILL).height(ALTURA_BARRA_SKILL).padBottom(10).row();

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
            Button slot = new Button(estilo);
            slot.setChecked(i == selectedItem);
            if (i < inventoryItems.size()) {
                InventoryItem item = inventoryItems.get(i);
                Stack conteudo = new Stack();
                TextureAtlas.AtlasRegion textura = iconeDoItem(item.itemPath);
                if (textura != null) {
                    Image icone = new Image(new TextureRegionDrawable(textura));
                    icone.setScaling(Scaling.fit);
                    conteudo.add(icone);
                }
                if (item.quantity > 1 || item.favorite) {
                    Table marcadores = new Table();
                    marcadores.bottom().right();
                    if (item.favorite) {
                        Label favorito = new Label("F", skin, "hud");
                        favorito.setColor(new Color(1f, 0.82f, 0.24f, 1f));
                        marcadores.add(favorito).left().expandX();
                    }
                    if (item.quantity > 1) {
                        Label quantidade = new Label(String.valueOf(item.quantity), skin, "hud");
                        quantidade.setColor(Color.WHITE);
                        marcadores.add(quantidade).right();
                    }
                    conteudo.add(marcadores);
                }
                slot.add(conteudo).grow().pad(4);
            }
            slot.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
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
            inventoryGrid.add(slot).size(42).pad(1);
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
        if (item == null) {
            itemName.setText("");
            itemQuantity.setText("");
            itemFavorite.setText("");
            actionStatus.setText("");
            favoriteButton.setDisabled(true);
            trashButton.setDisabled(true);
            return;
        }
        itemName.setText(nomeExibicao(item.itemPath));
        itemQuantity.setText(item.quantity > 1 ? "Quantity: " + item.quantity : "");
        itemFavorite.setText(item.favorite ? "Favorite" : "");
        actionStatus.setText("");
        favoriteButton.setDisabled(item.instanceId.isEmpty());
        trashButton.setDisabled(item.instanceId.isEmpty() || item.favorite);
        favoriteButton.setColor(item.favorite ? new Color(1f, 0.82f, 0.24f, 1f) : Color.WHITE);
        cancelarExclusao();
    }

    private Button criarBotaoAcao(String regiao, Runnable acao) {
        Button.ButtonStyle estilo = new Button.ButtonStyle();
        estilo.up = skin.getDrawable("bag-slot");
        estilo.over = skin.getDrawable("bag-slot-hover");
        estilo.down = skin.getDrawable("bag-slot-selected");
        Button botao = new Button(estilo);
        Image icone = new Image(new TextureRegionDrawable(atlas.findRegion(regiao)));
        icone.setScaling(Scaling.fit);
        botao.add(icone).size(24);
        botao.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                acao.run();
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
        InventoryItem item = itemSelecionado();
        if (item == null || item.favorite || item.instanceId.isEmpty()) return;
        confirmandoExclusao = true;
        actionStatus.setText("Delete this item?");
        actionButtons.setVisible(false);
        deleteConfirmation.setVisible(true);
    }

    private void confirmarExclusao() {
        InventoryItem item = itemSelecionado();
        if (!confirmandoExclusao || item == null || item.favorite || item.instanceId.isEmpty() || !socket.isConnected()) return;
        String payload = GameSocket.obj(w -> {
            w.array("instance_ids");
            w.value(item.instanceId);
            w.pop();
        });
        socket.emitRaw("delete_items", payload);
        selectedItem = -1;
        selectedInstanceId = "";
        atualizarDetalhes(null);
        confirmandoExclusao = false;
        actionStatus.setText("Deleting...");
        deleteConfirmation.setVisible(false);
    }

    private void cancelarExclusao() {
        confirmandoExclusao = false;
        if (actionButtons != null) actionButtons.setVisible(true);
        if (deleteConfirmation != null) deleteConfirmation.setVisible(false);
    }

    private InventoryItem itemSelecionado() {
        return selectedItem >= 0 && selectedItem < inventoryItems.size() ? inventoryItems.get(selectedItem) : null;
    }

    private String nomeExibicao(String caminho) {
        if (caminho == null || caminho.isEmpty()) return "Unknown item";
        String nome = caminho.substring(caminho.lastIndexOf('/') + 1);
        int extensao = nome.lastIndexOf('.');
        if (extensao >= 0) nome = nome.substring(0, extensao);
        nome = nome.replace("Starter", "").replaceAll("([a-z])([A-Z])", "$1 $2");
        return nome.isEmpty() ? "Item" : nome;
    }

    public void atualizarInventario(JsonValue dados) {
        inventoryItems.clear();
        if (dados != null && dados.isArray()) {
            for (JsonValue entrada = dados.child; entrada != null; entrada = entrada.next) {
                if (!entrada.isObject()) continue;
                String caminho = entrada.getString("item", "");
                if (caminho.isEmpty()) continue;
                inventoryItems.add(new InventoryItem(entrada.getString("id", ""), caminho,
                    Math.max(1, entrada.getInt("qty", 1)), entrada.getBoolean("favorite", false)));
            }
        }
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

    public void atualizarCapacidade(float usada, float maxima) {
        capacityUsed = usada;
        capacityMaximum = maxima;
        capacityLabel.setText(String.format("Capacity: %.1f / %.0f", usada, maxima));
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
            boolean comTitulo = !"Bag".equals(secao) && !"Skills".equals(secao);
            tituloSecao.setVisible(comTitulo);
            mainWindow.clearChildren();
            if (comTitulo) {
                mainWindow.add(tituloSecao).growX().left().padBottom(9).row();
            }
            Table pagina = "Equip".equals(secao) ? equipPage : "Skills".equals(secao) ? skillsPage : bagPage;
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

    public void alternar() {
        root.setVisible(!root.isVisible());
    }

    public void setVisible(boolean visible) {
        root.setVisible(visible);
    }

    public boolean isVisible() {
        return root.isVisible();
    }
}