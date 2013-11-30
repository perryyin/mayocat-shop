/*
 * Copyright (c) 2012, Mayocat <hello@mayocat.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.mayocat.theme.internal;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import javax.inject.Inject;

import org.mayocat.configuration.ConfigurationService;
import org.mayocat.context.WebContext;
import org.mayocat.files.FileManager;
import org.mayocat.theme.Breakpoint;
import org.mayocat.theme.Model;
import org.mayocat.theme.TemplateNotFoundException;
import org.mayocat.theme.Theme;
import org.mayocat.theme.ThemeDefinition;
import org.mayocat.theme.ThemeFileResolver;
import org.mayocat.theme.ThemeResource;
import org.mayocat.theme.UserAgentBreakpointDetector;
import org.mayocat.views.Template;
import org.slf4j.Logger;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.io.Files;
import com.google.common.io.Resources;

/**
 * @version $Id$
 */
public class DefaultThemeFileResolver implements ThemeFileResolver
{
    private static final String INDEX_HTML = "index.html";

    @Inject
    private FileManager fileManager;

    @Inject
    private WebContext context;

    @Inject
    private ConfigurationService configurationService;

    @Inject
    private UserAgentBreakpointDetector breakpointDetector;

    @Inject
    private Logger logger;

    @Override
    public Template getIndexTemplate(Breakpoint breakpoint) throws TemplateNotFoundException
    {
        try {
            String content = this.getTemplateContent(INDEX_HTML, breakpoint);
            Template template = new Template(generateTemplateId(INDEX_HTML, breakpoint), content);
            return template;
        } catch (IOException e) {
            throw new TemplateNotFoundException(e);
        }
    }

    @Override
    public Template getTemplate(String name, Breakpoint breakpoint) throws TemplateNotFoundException
    {
        try {
            String content = this.getTemplateContent(name, breakpoint);
            Template template = new Template(generateTemplateId(name, breakpoint), content, true);
            return template;
        } catch (IOException e) {
            throw new TemplateNotFoundException(e);
        }
    }

    @Override
    public ThemeResource getResource(String name, Breakpoint breakpoint)
    {
        try {
            ThemeResource result = getResource(getActiveTheme(), name, breakpoint);
            return result;
        } catch (IOException e) {
            this.logger.warn("I/O Exception while resolving resource [{}]", name, e);
            return null;
        }
    }

    @Override
    public Optional<String> resolveModelPath(String id)
    {
        Map<String, Model> models = getThemeDefinition().getModels();
        if (models.containsKey(id)) {
            return Optional.fromNullable(models.get(id).getFile());
        }
        return Optional.absent();
    }

    private String generateTemplateId(String layoutName, Breakpoint breakpoint)
    {
        String themeName = context.getTheme().getDefinition().getName();
        String templateId =
                themeName.length() + themeName + "_" + breakpoint.toString().length() + breakpoint.toString()
                        + "_" + layoutName.length() + layoutName;
        return "" + templateId.hashCode();
    }

    private String getTemplateContent(String name, Breakpoint breakpoint) throws TemplateNotFoundException, IOException
    {
        String result = getTemplateContent(getActiveTheme(), name, breakpoint);
        if (result == null) {
            throw new TemplateNotFoundException();
        }
        return result;
    }

    private ThemeResource getResource(Theme theme, String name, Breakpoint breakpoint) throws IOException
    {
        Path path = theme.getPath();
        if (breakpoint != null && breakpoint != Breakpoint.DEFAULT) {
            path = path.resolve(breakpoint.getFolder());
        }
        path = path.resolve(name);

        switch (theme.getType()) {
            case FILE_SYSTEM:
                if (path.toFile().exists()) {
                    return new ThemeResource(ThemeResource.Type.FILE, path);
                }
                break;

            case CLASSPATH:
                try {
                    Resources.getResource(path.toString());
                    return new ThemeResource(ThemeResource.Type.CLASSPATH_RESOURCE, path);
                } catch (IllegalArgumentException e) {
                    // Go on
                }
                break;
        }

        if (breakpoint != null && breakpoint != Breakpoint.DEFAULT) {
            // Maybe without the breakpoint
            return getResource(theme, name, Breakpoint.DEFAULT);
        }

        if (theme.getParent() != null) {

            // Not found yet and theme has parent : try in parent
            return getResource(theme.getParent(), name, breakpoint);
        }

        // Not found at all, nevermind
        return null;
    }

    /**
     * Tries to get template content for a certain theme and breakpoint. Tries in order : - tenant theme folder in
     * persistent directory (example : data/tenants/thetenant/themes/thetheme/[breakpoint/]filename - global theme
     * folder in persistent directory (example : data/themes/thetheme/[breakpoint/]filename) - classpath (example uri :
     * /themes/thetheme/[breakpoint/]filename) For each step, checks the breakpoint (if set) and fallback on the
     * no-breakpoint file
     *
     * @param theme the theme for which to try and get the template content
     * @param name the name of the template to get
     * @param breakpoint the breakpoint for which to get the template content
     * @return the content of the template, or null if not found
     * @throws IOException when there is an IO exception getting the content
     */
    private String getTemplateContent(Theme theme, String name, Breakpoint breakpoint) throws IOException
    {
        ThemeResource resource = this.getResource(theme, name, breakpoint);
        if (resource == null) {
            return null;
        }

        switch (resource.getType()) {
            default:
            case FILE:
                return Files.toString(resource.getPath().toFile(), Charsets.UTF_8);
            case CLASSPATH_RESOURCE:
                return Resources.toString(Resources.getResource(resource.getPath().toString()), Charsets.UTF_8);
        }
    }

    private ThemeDefinition getThemeDefinition()
    {
        return context.getTheme().getDefinition();
    }

    private Theme getActiveTheme()
    {
        return this.context.getTheme();
    }
}

