/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.guide;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.client.resources.Language;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import buildcraft.api.core.BCLog;

import buildcraft.lib.client.guide.data.GuideEntryLoader;
import buildcraft.lib.client.guide.data.JsonEntry;
import buildcraft.lib.client.guide.loader.IPageLoader;
import buildcraft.lib.client.guide.loader.MarkdownPageLoader;
import buildcraft.lib.client.guide.parts.GuidePageFactory;
import buildcraft.lib.client.guide.parts.GuidePageStandInRecipes;

public enum GuideManager implements IResourceManagerReloadListener {
    INSTANCE;

    private static final String DEFAULT_LANG = "en_us";
    private static final Map<String, IPageLoader> PAGE_LOADERS = new HashMap<>();

    private final List<PageEntry> entries = new ArrayList<>();
    private final Map<String, GuidePageFactory> pages = new HashMap<>();
    private final Map<ItemStack, GuidePageFactory> generatedPages = new HashMap<>();

    static {
        PAGE_LOADERS.put("md", MarkdownPageLoader.INSTANCE);
    }

    @Override
    public void onResourceManagerReload(IResourceManager resourceManager) {
        Stopwatch watch = Stopwatch.createStarted();
        entries.clear();
        List<JsonEntry> loaded = GuideEntryLoader.loadAll(resourceManager);
        for (JsonEntry entry : loaded) {
            entries.add(new PageEntry(entry));
        }
        pages.clear();

        Language currentLanguage = Minecraft.getMinecraft().getLanguageManager().getCurrentLanguage();
        String langCode;
        if (currentLanguage == null) {
            BCLog.logger.warn("Current language was null!");
            langCode = DEFAULT_LANG;
        } else {
            langCode = currentLanguage.getLanguageCode();
        }

        // load the default ones
        loadLangInternal(resourceManager, DEFAULT_LANG);
        // replace any existing with the new ones.

        if (!DEFAULT_LANG.equals(langCode)) {
            loadLangInternal(resourceManager, langCode);
        }
        watch.stop();
        long time = watch.elapsed(TimeUnit.MILLISECONDS);
        int p = entries.size();
        int a = pages.size();
        int e = p - a;
        BCLog.logger.info("[lib.guide] Loaded " + p + " possible and " + a + " actual guide pages (" + e
            + " not found) in " + time + "ms.");
    }

    private void loadLangInternal(IResourceManager resourceManager, String lang) {
        for (PageEntry data : entries) {
            String page = data.page.replaceAll("<lang>", lang);
            String ending = page.substring(page.lastIndexOf('.') + 1);

            IPageLoader loader = PAGE_LOADERS.get(ending);
            if (loader == null) {
                BCLog.logger.warn("[lib.guide.loader] Unable to load guide page '" + page
                    + "', as we don't know how to load it! (Known file type endings are " + PAGE_LOADERS.keySet()
                    + ")");
                continue;
            }
            try (InputStream stream = resourceManager.getResource(new ResourceLocation(page)).getInputStream()) {
                GuidePageFactory factory = loader.loadPage(stream, data);
                // put the original page in so that the different lang variants override it
                pages.put(data.page, factory);
                if (GuideEntryLoader.DEBUG) {
                    BCLog.logger.info("[lib.guide.loader] Loaded page '" + page + "'.");
                }
            } catch (FileNotFoundException fnfe) {
                BCLog.logger.warn("[lib.guide.loader] Unable to load guide page '" + page
                    + "' because we couldn't find it in any resource pack!");
            } catch (IOException io) {
                io.printStackTrace();
            }
        }
    }

    public ImmutableList<PageEntry> getAllEntries() {
        return ImmutableList.copyOf(entries);
    }

    public GuidePageFactory getFactoryFor(PageEntry entry) {
        return pages.get(entry.page);
    }

    public PageEntry getEntryFor(@Nonnull ItemStack stack) {
        for (PageEntry entry : entries) {
            if (entry.stackMatches(stack)) {
                return entry;
            }
        }
        return null;
    }

    @Nonnull
    public GuidePageFactory getPageFor(@Nonnull ItemStack stack) {
        PageEntry entry = getEntryFor(stack);
        if (entry != null) {
            GuidePageFactory factory = getFactoryFor(entry);
            if (factory != null) {
                return factory;
            }
        }
        // Create a dummy page for the stack
        return generatedPages.computeIfAbsent(stack, GuidePageStandInRecipes::createFactory);
    }
}
