package com.elmakers.mine.bukkit.action.builtin;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.inventory.ItemStack;

import com.elmakers.mine.bukkit.api.action.CastContext;
import com.elmakers.mine.bukkit.api.magic.CasterProperties;
import com.elmakers.mine.bukkit.utility.ConfigurationUtils;
import com.elmakers.mine.bukkit.wand.Wand;

public class ShopAction extends SelectorAction {

    @Override
    public void prepare(CastContext context, ConfigurationSection parameters) {
        // Don't load items as defaults
        Object itemDefaults = parameters.get("items");
        parameters.set("items", null);

        // Sell shop overrides
        if (parameters.getBoolean("sell")) {
            // Support scale parameter
            parameters.set("earn_scale", parameters.get("earn_scale"));
            parameters.set("scale", null);

            // Supply selected message
            if (!parameters.contains("selected")) {
                parameters.set("selected", context.getController().getMessages().get("shops.sold"));
            }
        }

        super.prepare(context, parameters);

        // Restore items list. This is kind of messy, but so is this whole action.
        parameters.set("items", itemDefaults);
        loadItems(context, parameters, "items", false);
        loadItems(context, parameters, "spells", true);
    }

    protected void loadItems(CastContext context, ConfigurationSection parameters, String key, boolean filterSpells) {
        if (parameters.contains(key)) {
            List<ConfigurationSection> itemConfigs = new ArrayList<>();
            if (parameters.isConfigurationSection(key)) {
                ConfigurationSection itemSection = parameters.getConfigurationSection(key);
                Set<String> itemKeys = itemSection.getKeys(false);
                for (String itemKey : itemKeys) {
                    ConfigurationSection itemConfig = new MemoryConfiguration();
                    itemConfig.set("item", itemKey);
                    itemConfig.set("cost", itemSection.get(itemKey));
                    itemConfigs.add(itemConfig);
                }
            } else {
                List<? extends Object> objects = parameters.getList(key);
                for (Object object : objects) {
                    if (object instanceof ConfigurationSection) {
                        itemConfigs.add((ConfigurationSection)object);
                    } else if (object instanceof Map) {
                         itemConfigs.add(ConfigurationUtils.toConfigurationSection((Map<?, ?>)object));
                    } else if (object instanceof String) {
                        ConfigurationSection itemConfig = new MemoryConfiguration();
                        itemConfig.set("item", object);
                        itemConfigs.add(itemConfig);
                    } else {
                        context.getLogger().warning("Invalid item in shop config: " + object);
                    }
                }
            }

            if (filterSpells) {
                CasterProperties caster = context.getMage().getActiveProperties();
                Iterator<ConfigurationSection> it = itemConfigs.iterator();
                while (it.hasNext()) {
                    ConfigurationSection config = it.next();
                    String spellName = config.getString("item");
                    if (spellName != null && caster.hasSpell(spellName)) {
                        it.remove();
                    }
                }
            }

            if (parameters.getBoolean("sell", false)) {
                for (ConfigurationSection itemConfig : itemConfigs) {
                    String itemName = itemConfig.getString("item");
                    if (itemName == null || itemName.equalsIgnoreCase("none")) continue;

                    itemConfig.set("item", null);
                    itemConfig.set("icon", itemName);
                    ItemStack item = parseItem(itemName);
                    Object costs = itemConfig.get("cost");
                    if (costs != null) {
                        itemConfig.set("earn", costs);
                        itemConfig.set("cost", null);
                    } else if (item != null) {
                        double worth = context.getController().getWorth(item);
                        if (worth > 0) {
                            itemConfig.set("earn", worth);
                        }
                    }
                    ConfigurationSection costSection = itemConfig.createSection("costs");
                    Integer sp = Wand.getSP(item);
                    if (sp != null) {
                        costSection.set("sp", sp);
                    } else {
                        costSection.set(itemName, item.getAmount());
                    }
                }
            }
            loadOptions(itemConfigs);
        }
    }
}