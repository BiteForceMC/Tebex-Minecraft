package io.tebex.plugin.gui;

import io.tebex.plugin.BukkitPluginPlatform;
import io.tebex.plugin.util.MaterialUtil;
import io.tebex.sdk.obj.Category;
import io.tebex.sdk.obj.CategoryPackage;
import io.tebex.sdk.obj.CheckoutUrl;
import io.tebex.sdk.obj.ICategory;
import io.tebex.sdk.obj.SubCategory;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;

import java.text.DecimalFormat;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class BuyGUI {
    private final BukkitPluginPlatform platform;

    public BuyGUI(BukkitPluginPlatform platform) {
        this.platform = platform;
    }

    private FileConfiguration getConfig() {
        return platform.getPlugin().getConfig();
    }

    public void open(Player player) {
        List<Category> categories = platform.getStoreCategories();
        if (categories == null) {
            player.sendMessage(ChatColor.RED + "Failed to get listing. Please contact an administrator.");
            return;
        }

        ListingGui listingGui = new ListingGui()
                .title(remapLegacyFormatSeparator(getConfig().getString("gui.menu.home.title", "Server Shop")))
                .rows(getConfig().getInt("gui.menu.home.rows") < 1 ? categories.size() / 9 + 1
                        : getConfig().getInt("gui.menu.home.rows"))
                .create();

        categories.sort(Comparator.comparingInt(Category::getOrder));

        categories.forEach(category -> {
            listingGui.addItem(getCategoryItemBuilder(category).asGuiItem(action -> {
                action.setCancelled(true);
                openCategoryMenu(player, category);
            }));
        });

        platform.executeBlocking(() -> listingGui.open(player));
    }

    public void open(Player player, String menu) {
        if (menu == null || menu.trim().isEmpty()) {
            open(player);
            return;
        }

        List<Category> categories = platform.getStoreCategories();
        if (categories == null) {
            player.sendMessage(ChatColor.RED + "Failed to get listing. Please contact an administrator.");
            return;
        }

        ICategory matchedCategory = resolveCategoryByInput(categories, menu);
        if (matchedCategory == null) {
            player.sendMessage(ChatColor.RED + "Store category '" + menu + "' was not found.");
            return;
        }

        openCategoryMenu(player, matchedCategory);
    }

    public List<String> getMenuSuggestions(String input) {
        List<Category> categories = platform.getStoreCategories();
        if (categories == null) {
            return java.util.Collections.emptyList();
        }

        String normalizedInput = normalizeMenuInput(input);
        Set<String> suggestions = new LinkedHashSet<>();

        for (Category category : categories) {
            addSuggestionValues(category, suggestions);
            if (category.getSubCategories() != null) {
                for (SubCategory subCategory : category.getSubCategories()) {
                    addSuggestionValues(subCategory, suggestions);
                }
            }
        }

        return suggestions.stream()
                .filter(menuKey -> normalizedInput.isEmpty() || menuKey.startsWith(normalizedInput))
                .collect(Collectors.toList());
    }

    private ICategory findCategoryByInput(List<Category> categories, String input) {
        String normalizedInput = normalizeMenuInput(input);
        for (Category category : categories) {
            if (isMenuMatch(category, input, normalizedInput)) {
                return category;
            }

            if (category.getSubCategories() != null) {
                for (SubCategory subCategory : category.getSubCategories()) {
                    if (isMenuMatch(subCategory, input, normalizedInput)) {
                        return subCategory;
                    }
                }
            }
        }

        return null;
    }

    private ICategory resolveCategoryByInput(List<Category> categories, String input) {
        ICategory matchedCategory = findCategoryByInput(categories, input);
        if (matchedCategory != null) {
            return matchedCategory;
        }

        List<String> suggestions = getMenuSuggestions(input);
        if (suggestions.size() == 1) {
            return findCategoryByInput(categories, suggestions.get(0));
        }

        return null;
    }

    private boolean isMenuMatch(ICategory category, String input, String normalizedInput) {
        return String.valueOf(category.getId()).equalsIgnoreCase(input)
                || category.getName().equalsIgnoreCase(input)
                || toMenuKey(category.getName()).equals(normalizedInput);
    }

    private void addSuggestionValues(ICategory category, Set<String> suggestions) {
        String menuKey = toMenuKey(category.getName());
        if (!menuKey.isEmpty()) {
            suggestions.add(menuKey);
        }
    }

    private String toMenuKey(String input) {
        return normalizeMenuInput(input);
    }

    private String normalizeMenuInput(String input) {
        if (input == null) {
            return "";
        }

        return input.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    private void openCategoryMenu(Player player, ICategory category) {
        int configRows = getConfig().getInt("gui.menu.category.rows");
        int neededRows = (category.getPackages().size() / 9) + 1;

        if (configRows < neededRows) {
            configRows = neededRows;
        }

        ListingGui subListingGui = new ListingGui()
                .title(remapLegacyFormatSeparator(
                        getConfig().getString("gui.menu.category.title").replace("%category%", category.getName())))
                .rows(configRows)
                .create();

        category.getPackages().sort(Comparator.comparingInt(CategoryPackage::getOrder));

        if (category instanceof Category) {
            Category cat = (Category) category;

            if (cat.getSubCategories() != null) {
                cat.getSubCategories().forEach(
                        subCategory -> subListingGui.addItem(getCategoryItemBuilder(subCategory).asGuiItem(action -> {
                            action.setCancelled(true);
                            openCategoryMenu(player, subCategory);
                        })));

                TebexGuiItem backItem = getBackItemBuilder().asGuiItem(action -> {
                    action.setCancelled(true);
                    open(player);
                });
                int backItemSlot = subListingGui.getRows() * 9 - 5;
                subListingGui.addItem(backItemSlot, backItem);
                // subListingGui.setItem(backItemSlot, backItem);
            }
        } else if (category instanceof SubCategory) {
            SubCategory subCategory = (SubCategory) category;

            subListingGui.updateTitle(remapLegacyFormatSeparator(getConfig().getString("gui.menu.sub-category.title")
                    .replace("%category%", subCategory.getParent().getName())
                    .replace("%sub_category%", category.getName())));

            TebexGuiItem backItem = getBackItemBuilder().asGuiItem(action -> {
                action.setCancelled(true);
                openCategoryMenu(player, subCategory.getParent());
            });
            int backItemSlot = subListingGui.getRows() * 9 - 5;

            subListingGui.addItem(backItemSlot, backItem);
            // subListingGui.setItem(subListingGui.getRows() * 9 - 5,backItem);
        }

        category.getPackages().forEach(
                categoryPackage -> subListingGui.addItem(getPackageItemBuilder(categoryPackage).asGuiItem(action -> {
                    action.setCancelled(true);
                    player.closeInventory();

                    createCheckoutForPlayer(player, categoryPackage);
                })));

        platform.executeBlocking(() -> subListingGui.open(player));
    }

    private void createCheckoutForPlayer(Player player, CategoryPackage categoryPackage) {
        String recipientName = player.getName();
        String checkoutUsername = platform.resolveCheckoutUsername(recipientName);

        createCheckoutUrlWithFallback(categoryPackage.getId(), checkoutUsername, recipientName)
                .thenAccept(checkout -> platform.sendCheckoutLink(recipientName, checkout.getUrl()))
                .exceptionally(ex -> {
                    sendWebstoreFallback(player, categoryPackage, checkoutUsername, ex);
                    return null;
                });
    }

    private CompletableFuture<CheckoutUrl> createCheckoutUrlWithFallback(int packageId, String preferredUsername,
                                                                          String fallbackUsername) {
        CompletableFuture<CheckoutUrl> checkoutFuture = new CompletableFuture<>();

        platform.getSDK().createCheckoutUrl(packageId, preferredUsername).whenComplete((checkout, error) -> {
            if (error == null) {
                checkoutFuture.complete(checkout);
                return;
            }

            if (preferredUsername.equalsIgnoreCase(fallbackUsername)) {
                checkoutFuture.completeExceptionally(error);
                return;
            }

            platform.debug("Checkout URL creation failed for username '" + preferredUsername
                    + "'. Retrying with '" + fallbackUsername + "'.");
            platform.getSDK().createCheckoutUrl(packageId, fallbackUsername).whenComplete((retryCheckout, retryError) -> {
                if (retryError == null) {
                    checkoutFuture.complete(retryCheckout);
                    return;
                }

                checkoutFuture.completeExceptionally(retryError);
            });
        });

        return checkoutFuture;
    }

    private void sendWebstoreFallback(Player player, CategoryPackage categoryPackage, String attemptedUsername,
                                      Throwable throwable) {
        String fallbackUrl = platform.getWebstoreUrl();
        player.sendMessage(ChatColor.RED + "Unable to create a direct checkout link for this package.");
        player.sendMessage(ChatColor.YELLOW + "Open the webstore to continue: " + fallbackUrl);

        platform.warning(
                "Failed to create checkout URL for package " + categoryPackage.getId() + " and player '"
                        + attemptedUsername + "': " + getErrorMessage(throwable),
                "The player has been sent the webstore URL as a fallback."
        );
    }

    private String getErrorMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }

        if (current == null || current.getMessage() == null || current.getMessage().trim().isEmpty()) {
            return "Unknown error";
        }

        return current.getMessage();
    }

    private TebexItemBuilder getCategoryItemBuilder(ICategory category) {
        ConfigurationSection section = getConfig().getConfigurationSection("gui.item.category");

        String itemType = section.getString("material");
        Material defaultMaterial = MaterialUtil.fromString(itemType).isPresent()
                ? MaterialUtil.fromString(itemType).get().parseMaterial()
                : null;
        Material material = MaterialUtil.fromString(category.getGuiItem()).isPresent()
                ? MaterialUtil.fromString(category.getGuiItem()).get().parseMaterial()
                : defaultMaterial;

        String name = section.getString("name");
        List<String> lore = section.getStringList("lore");

        return TebexItemBuilder.from(material != null ? material : Material.BOOK)
                .flags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE)
                .name(name != null ? remapLegacyFormatSeparator(italicize(handlePlaceholders(category, name)))
                        : remapLegacyFormatSeparator(category.getName()))
                .lore(lore.stream()
                        .map(line -> remapLegacyFormatSeparator(italicize(handlePlaceholders(category, line))))
                        .collect(Collectors.toList()));
    }

    private TebexItemBuilder getPackageItemBuilder(CategoryPackage categoryPackage) {
        ConfigurationSection section = getConfig()
                .getConfigurationSection("gui.item." + (categoryPackage.hasSale() ? "package-sale" : "package"));

        if (section == null) {
            platform.warning(
                    "Invalid configuration section for " + (categoryPackage.hasSale() ? "package-sale" : "package"),
                    "Check that your definition for `" + categoryPackage.getName() + "` in config.yml is valid.");
            return null;
        }

        String itemType = section.getString("material");

        Material defaultMaterial = MaterialUtil.fromString(itemType).isPresent()
                ? MaterialUtil.fromString(itemType).get().parseMaterial()
                : null;
        Material material = MaterialUtil.fromString(categoryPackage.getItemId()).isPresent()
                ? MaterialUtil.fromString(categoryPackage.getItemId()).get().parseMaterial()
                : defaultMaterial;

        String name = section.getString("name");
        List<String> lore = section.getStringList("lore");

        TebexItemBuilder itemBuilder = TebexItemBuilder.from(material != null ? material : Material.BOOK)
                .flags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE)
                .name(name != null ? remapLegacyFormatSeparator(italicize(handlePlaceholders(categoryPackage, name)))
                        : remapLegacyFormatSeparator(categoryPackage.getName()))
                .lore(lore.stream()
                        .map(line -> remapLegacyFormatSeparator(italicize(handlePlaceholders(categoryPackage, line))))
                        .collect(Collectors.toList()));

        if (categoryPackage.hasSale()) {
            itemBuilder.enchant();
        }

        return itemBuilder;
    }

    private String italicize(String input) {
        return "§o" + input + "§r";
    }

    private String remapLegacyFormatSeparator(String input) {
        return input.replaceAll("&", "§");
    }

    private TebexItemBuilder getBackItemBuilder() {
        ConfigurationSection section = getConfig().getConfigurationSection("gui.item.back");

        String itemType = section.getString("material");
        Material defaultMaterial = MaterialUtil.fromString(itemType).isPresent()
                ? MaterialUtil.fromString(itemType).get().parseMaterial()
                : null;
        Material material = MaterialUtil.fromString(section.getString("material")).isPresent()
                ? MaterialUtil.fromString(section.getString("material")).get().parseMaterial()
                : defaultMaterial;

        String name = section.getString("name");
        List<String> lore = section.getStringList("lore");

        return TebexItemBuilder.from(material != null ? material : Material.BOOK)
                .flags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE)
                .name(name != null ? remapLegacyFormatSeparator(italicize(name)) : "Back")
                .lore(lore.stream().map(line -> remapLegacyFormatSeparator(italicize(line)))
                        .collect(Collectors.toList()));
    }

    private String handlePlaceholders(Object obj, String str) {
        if (obj instanceof ICategory) {
            ICategory category = (ICategory) obj;

            str = str.replace("%category%", category.getName());
        } else if (obj instanceof CategoryPackage) {
            CategoryPackage categoryPackage = (CategoryPackage) obj;

            DecimalFormat decimalFormat = new DecimalFormat("#.##");

            str = str
                    .replace("%package_name%", categoryPackage.getName())
                    .replace("%package_price%", decimalFormat.format(categoryPackage.getPrice()))
                    .replace("%package_currency_name%",
                            platform.getStoreInformation().getStore().getCurrency().getIso4217())
                    .replace("%package_currency%", platform.getStoreInformation().getStore().getCurrency().getSymbol());

            if (categoryPackage.hasSale()) {
                str = str
                        .replace("%package_discount%", decimalFormat.format(categoryPackage.getSale().getDiscount()))
                        .replace("%package_sale_price%", decimalFormat
                                .format(categoryPackage.getPrice() - categoryPackage.getSale().getDiscount()));
            }
        }

        return str;
    }
}
