package io.tebex.plugin.gui;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import io.tebex.plugin.TebexPlugin;
import io.tebex.plugin.util.ItemUtil;
import io.tebex.sdk.obj.Category;
import io.tebex.sdk.obj.CategoryPackage;
import io.tebex.sdk.obj.ICategory;
import io.tebex.sdk.obj.SubCategory;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.*;
import net.minecraft.util.Identifier;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

public class BuyGUI {
    private static final int MIN_ROWS = 1;
    private static final int MAX_ROWS = 6;

    private final TebexPlugin platform;
    private final YamlDocument config;

    public BuyGUI(TebexPlugin platform) {
        this.platform = platform;
        this.config = platform.getPlatformConfig().getYamlDocument();
    }

    private ScreenHandlerType<GenericContainerScreenHandler> getScreenHandlerType(final int rows) {
        ScreenHandlerType<GenericContainerScreenHandler> type;
        switch (rows) {
            case 1 -> type = ScreenHandlerType.GENERIC_9X1;
            case 2 -> type = ScreenHandlerType.GENERIC_9X2;
            case 3 -> type = ScreenHandlerType.GENERIC_9X3;
            case 4 -> type = ScreenHandlerType.GENERIC_9X4;
            case 5 -> type = ScreenHandlerType.GENERIC_9X5;
            default -> type = ScreenHandlerType.GENERIC_9X6;
        }
        return type;
    }

    private String convertToLegacyString(String str) {
        return str.replace("&", "\u00A7");
    }

    public void open(ServerPlayerEntity player) {
        List<Category> categories = platform.getStoreCategories();
        if (categories == null) {
            player.sendMessage(Text.of("Failed to get listing. Please contact an administrator."), false);
            platform.warning("Player " + player.getName() + " used buy command, but no listings are active in your store.","Ensure your store is set up and has at least one active listing. Use /tebex reload to load new listings.");
            return;
        }

        openHomeMenu(player, categories, 0);
    }

    private void openHomeMenu(ServerPlayerEntity player, List<Category> categories, int page) {
        List<Category> sortedCategories = new ArrayList<>(categories);
        sortedCategories.sort(Comparator.comparingInt(Category::getOrder));

        List<TebexGuiItem> homeItems = new ArrayList<>();
        sortedCategories.forEach(category -> homeItems.add(getCategoryItemBuilder(category).asGuiItem(action -> {
            action.setCancelled(true);
            openCategoryMenu(player, category);
        })));

        openPagedMenu(
                player,
                config.getString("gui.menu.home.title", "Server Shop"),
                config.getInt("gui.menu.home.rows"),
                homeItems,
                null,
                targetPage -> openHomeMenu(player, sortedCategories, targetPage),
                page
        );
    }

    private void openCategoryMenu(ServerPlayerEntity player, ICategory category) {
        openCategoryMenu(player, category, 0);
    }

    private void openCategoryMenu(ServerPlayerEntity player, ICategory category, int page) {
        List<TebexGuiItem> categoryItems = new ArrayList<>();
        String title = config.getString("gui.menu.category.title", "Viewing %category%")
                .replace("%category%", category.getName());

        TebexGuiItem backItem = getBackItemBuilder().asGuiItem(action -> {
            action.setCancelled(true);
            open(player);
        });

        if (category instanceof Category cat) {
            if (cat.getSubCategories() != null) {
                cat.getSubCategories().forEach(subCategory -> categoryItems.add(getCategoryItemBuilder(subCategory).asGuiItem(action -> {
                    action.setCancelled(true);
                    openCategoryMenu(player, subCategory);
                })));
            }
        } else if (category instanceof SubCategory) {
            SubCategory subCategory = (SubCategory) category;
            title = config.getString("gui.menu.sub-category.title", "Viewing %sub_category% (%category%)")
                    .replace("%category%", subCategory.getParent().getName())
                    .replace("%sub_category%", category.getName());

            backItem = getBackItemBuilder().asGuiItem(action -> {
                action.setCancelled(true);
                openCategoryMenu(player, subCategory.getParent());
            });
        }

        List<CategoryPackage> categoryPackages = new ArrayList<>(category.getPackages());
        categoryPackages.sort(Comparator.comparingInt(CategoryPackage::getOrder));

        categoryPackages.forEach(categoryPackage -> {
            TebexItemBuilder packageItemBuilder = getPackageItemBuilder(categoryPackage);
            if (packageItemBuilder == null) {
                return;
            }

            categoryItems.add(packageItemBuilder.asGuiItem(action -> {
                action.setCancelled(true);
                player.closeHandledScreen();

                // Create Checkout Url
                platform.getSDK().createCheckoutUrl(categoryPackage.getId(), player.getName().getString()).thenAccept(checkout -> {
                    player.sendMessage(Text.of("\u00A7aYou can checkout here: "), false);
                    player.sendMessage(MutableText.of(PlainTextContent.of("\u00A7a" + checkout.getUrl())).setStyle(Style.EMPTY.withClickEvent(
                            new ClickEvent(ClickEvent.Action.OPEN_URL, checkout.getUrl()))), false);
                }).exceptionally(ex -> {
                    player.sendMessage(Text.of("\u00A7cFailed to create checkout URL. Please contact an administrator."), false);
                    platform.error("Failed to create checkout URL for a user.", ex);
                    return null;
                });
            }));
        });

        openPagedMenu(
                player,
                title,
                config.getInt("gui.menu.category.rows"),
                categoryItems,
                backItem,
                targetPage -> openCategoryMenu(player, category, targetPage),
                page
        );
    }

    private void openPagedMenu(
            ServerPlayerEntity player,
            String title,
            int configuredRows,
            List<TebexGuiItem> contentItems,
            TebexGuiItem backItem,
            IntConsumer openPageAction,
            int page
    ) {
        int rows = resolveRows(configuredRows, contentItems.size(), backItem != null);
        int totalPages = getTotalPages(contentItems.size(), rows, backItem != null);
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));
        boolean hasNavigation = totalPages > 1;

        ListingGui listingGui = new ListingGui(rows, getScreenHandlerType(rows), player);
        listingGui.setTitle(Text.of(convertToLegacyString(title)).getString());

        List<Integer> contentSlots = getContentSlots(rows, backItem != null, hasNavigation);
        int pageSize = Math.max(1, contentSlots.size());
        int startIndex = currentPage * pageSize;
        int endIndex = Math.min(startIndex + pageSize, contentItems.size());

        for (int i = startIndex; i < endIndex; i++) {
            int slot = contentSlots.get(i - startIndex);
            listingGui.addItem(slot, contentItems.get(i));
        }

        if (backItem != null) {
            listingGui.addItem(getBackSlot(rows), backItem);
        }

        if (hasNavigation && currentPage > 0) {
            TebexGuiItem previousItem = getNavigationItemBuilder(false).asGuiItem(action -> {
                action.setCancelled(true);
                openPageAction.accept(currentPage - 1);
            });
            listingGui.addItem(getPreviousPageSlot(rows), previousItem);
        }

        if (hasNavigation && currentPage < totalPages - 1) {
            TebexGuiItem nextItem = getNavigationItemBuilder(true).asGuiItem(action -> {
                action.setCancelled(true);
                openPageAction.accept(currentPage + 1);
            });
            listingGui.addItem(getNextPageSlot(rows), nextItem);
        }

        listingGui.open();
    }

    private int resolveRows(int configuredRows, int contentItems, boolean hasBackButton) {
        if (configuredRows > 0) {
            return clampRows(configuredRows);
        }

        int reservedSlots = hasBackButton ? 1 : 0;
        int neededRows = (int) Math.ceil((contentItems + reservedSlots) / 9.0);
        return clampRows(Math.max(neededRows, MIN_ROWS));
    }

    private int clampRows(int rows) {
        return Math.max(MIN_ROWS, Math.min(MAX_ROWS, rows));
    }

    private int getTotalPages(int contentItems, int rows, boolean hasBackButton) {
        int pageSizeWithoutNavigation = getPageSize(rows, hasBackButton, false);
        int totalPages = Math.max(1, (int) Math.ceil(contentItems / (double) pageSizeWithoutNavigation));

        if (totalPages > 1) {
            int pageSizeWithNavigation = getPageSize(rows, hasBackButton, true);
            totalPages = Math.max(1, (int) Math.ceil(contentItems / (double) pageSizeWithNavigation));
        }

        return totalPages;
    }

    private int getPageSize(int rows, boolean hasBackButton, boolean hasNavigation) {
        int reservedSlots = (hasBackButton ? 1 : 0) + (hasNavigation ? 2 : 0);
        return Math.max(1, rows * 9 - reservedSlots);
    }

    private List<Integer> getContentSlots(int rows, boolean hasBackButton, boolean hasNavigation) {
        int inventorySize = rows * 9;
        int backSlot = hasBackButton ? getBackSlot(rows) : -1;
        int previousPageSlot = hasNavigation ? getPreviousPageSlot(rows) : -1;
        int nextPageSlot = hasNavigation ? getNextPageSlot(rows) : -1;

        List<Integer> slots = new ArrayList<>(inventorySize);
        for (int slot = 0; slot < inventorySize; slot++) {
            if (slot == backSlot || slot == previousPageSlot || slot == nextPageSlot) {
                continue;
            }
            slots.add(slot);
        }

        return slots;
    }

    private int getBackSlot(int rows) {
        return rows * 9 - 5;
    }

    private int getPreviousPageSlot(int rows) {
        return rows * 9 - 6;
    }

    private int getNextPageSlot(int rows) {
        return rows * 9 - 4;
    }

    private TebexItemBuilder getNavigationItemBuilder(boolean isNextPage) {
        String title = isNextPage ? "&aNext Page" : "&aPrevious Page";

        return TebexItemBuilder.from(Items.ARROW)
                .hideFlags(DataComponentTypes.ENCHANTMENTS, DataComponentTypes.ATTRIBUTE_MODIFIERS, DataComponentTypes.UNBREAKABLE)
                .name(remapLegacyFormatSeparator(italicize(title)))
                .lore(List.of());
    }

    private TebexItemBuilder getCategoryItemBuilder(ICategory category) {
        Section section = config.getSection("gui.item.category");

        String itemType = section.getString("material");

        Item defaultItem = ItemUtil.fromString(itemType).isPresent() ? ItemUtil.fromString(itemType).get() : null;
        Item item = ItemUtil.fromString(category.getGuiItem()).isPresent() ? ItemUtil.fromString(category.getGuiItem()).get() : defaultItem;

        String name = section.getString("name");
        List<String> lore = section.getStringList("lore");

        return TebexItemBuilder.from(item != null ? item : Items.BOOK)
                .hideFlags(DataComponentTypes.ENCHANTMENTS, DataComponentTypes.ATTRIBUTE_MODIFIERS, DataComponentTypes.UNBREAKABLE)
                .name(name != null ? remapLegacyFormatSeparator(italicize(handlePlaceholders(category, name))) : remapLegacyFormatSeparator(category.getName()))
                .lore(lore.stream().map(line ->  remapLegacyFormatSeparator(italicize(handlePlaceholders(category, line)))).collect(Collectors.toList()));
    }

    private TebexItemBuilder getPackageItemBuilder(CategoryPackage categoryPackage) {
        Section section = config.getSection("gui.item." + (categoryPackage.hasSale() ? "package-sale" : "package"));

        if (section == null) {
            platform.warning("Invalid configuration section for " + (categoryPackage.hasSale() ? "package-sale" : "package"), "Check that your package definition for `" + categoryPackage.getName() + "` in config.yml is valid.");
            return null;
        }

        String itemType = section.getString("material");
        Item material = Registries.ITEM.get(Identifier.tryParse(itemType.toLowerCase()));

        String name = section.getString("name");
        List<String> lore = section.getStringList("lore");

        MutableText guiName = MutableText.of(PlainTextContent.of(convertToLegacyString(name != null ? handlePlaceholders(categoryPackage, name) : categoryPackage.getName()))).setStyle(Style.EMPTY.withItalic(true));
        List<String> guiLore = lore.stream().map(line -> MutableText.of(PlainTextContent.of(convertToLegacyString(handlePlaceholders(categoryPackage, line)))).setStyle(Style.EMPTY.withItalic(true)).getString()).collect(Collectors.toList());

        TebexItemBuilder guiElementBuilder = TebexItemBuilder.from(material.asItem() != null ? material : Items.BOOK)
                .hideFlags(DataComponentTypes.ENCHANTMENTS, DataComponentTypes.ATTRIBUTE_MODIFIERS, DataComponentTypes.UNBREAKABLE)
                .name(guiName.getString())
                .lore(guiLore);

        if (categoryPackage.hasSale()) {
            guiElementBuilder.enchant();
        }

        return guiElementBuilder;
    }

    private TebexItemBuilder getBackItemBuilder() {
        Section section = config.getSection("gui.item.back");

        String itemType = section.getString("material");
        Item material = Registries.ITEM.get(Identifier.tryParse(itemType.toLowerCase()));

        String name = section.getString("name");
        List<String> lore = section.getStringList("lore");

        return TebexItemBuilder.from(material.asItem() != null ? material : Items.BOOK)
                .hideFlags(DataComponentTypes.ENCHANTMENTS, DataComponentTypes.ATTRIBUTE_MODIFIERS, DataComponentTypes.UNBREAKABLE)
                .name(Text.of(convertToLegacyString(name != null ? name : "\u00A7fBack")).getString())
                .lore(lore.stream().map(line -> ((MutableText)(Text.of(convertToLegacyString(line)))).setStyle(Style.EMPTY.withItalic(true)).getString()).collect(Collectors.toList()));
    }

    private String handlePlaceholders(Object obj, String str) {
        if (obj instanceof ICategory category) {
            str = str.replace("%category%", category.getName());
        } else if (obj instanceof CategoryPackage categoryPackage) {
            DecimalFormat decimalFormat = new DecimalFormat("#.##");

            str = str
                    .replace("%package_name%", categoryPackage.getName())
                    .replace("%package_price%", decimalFormat.format(categoryPackage.getPrice()))
                    .replace("%package_currency_name%", platform.getStoreInformation().getStore().getCurrency().getIso4217())
                    .replace("%package_currency%", platform.getStoreInformation().getStore().getCurrency().getSymbol());

            if (categoryPackage.hasSale()) {
                str = str
                        .replace("%package_discount%", decimalFormat.format(categoryPackage.getSale().getDiscount()))
                        .replace("%package_sale_price%", decimalFormat.format(categoryPackage.getPrice() - categoryPackage.getSale().getDiscount()));
            }
        }

        return str;
    }

    private String italicize(String input) {
        return "\u00A7o" + input + "\u00A7r";
    }

    private String remapLegacyFormatSeparator(String input) {
        return input.replaceAll("&", "\u00A7");
    }
}
