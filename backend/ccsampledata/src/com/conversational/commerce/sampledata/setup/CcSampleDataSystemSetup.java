package com.conversational.commerce.sampledata.setup;

import com.conversational.commerce.core.engine.config.CcConfigurationService;
import de.hybris.platform.commerceservices.setup.AbstractSystemSetup;
import de.hybris.platform.core.initialization.SystemSetup;
import de.hybris.platform.core.initialization.SystemSetup.Process;
import de.hybris.platform.core.initialization.SystemSetup.Type;
import de.hybris.platform.core.initialization.SystemSetupContext;
import de.hybris.platform.core.initialization.SystemSetupParameter;
import de.hybris.platform.core.initialization.SystemSetupParameterMethod;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Essential data (configuration row and toggles) always loads. Each pack is an option (ccsampledata.pack.&lt;code&gt;);
 * all five can be loaded together and coexist (D32). Project data and orders load only on synthetic-data sites (A2).
 * ASSUMPTION AS-10: follows the platform's AbstractSystemSetup pattern from memory. NOT COMPILED here.
 */
@SystemSetup(extension = "ccsampledata")
public class CcSampleDataSystemSetup extends AbstractSystemSetup
{
    private static final Logger LOG = LogManager.getLogger(CcSampleDataSystemSetup.class);
    private static final List<String> PACKS = List.of("tyres", "paints", "electrical", "cement", "plywood");
    private static final String IMPORT_ROOT = "/ccsampledata/import/";

    @Resource
    private SampleOrderLoader sampleOrderLoader;

    @Resource(name = "ccConfigurationService")
    private CcConfigurationService configurationService;

    @SystemSetup(type = Type.ESSENTIAL, process = Process.ALL)
    public void createEssentialData(final SystemSetupContext context)
    {
        importImpexFile(context, IMPORT_ROOT + "essential/essentialdata-cc-config.impex");
    }

    @SystemSetup(type = Type.PROJECT, process = Process.ALL)
    public void createProjectData(final SystemSetupContext context)
    {
        boolean any = false;
        for (final String pack : PACKS)
        {
            if (getBooleanSystemSetupParameter(context, "ccsampledata.pack." + pack))
            {
                importImpexFile(context, IMPORT_ROOT + "projectdata/pack-" + pack + ".impex");
                any = true;
            }
        }
        if (any)
        {
            importImpexFile(context, IMPORT_ROOT + "projectdata/all-packs-dealer.impex");
        }
        // Orders need the products and customers from the ImpEx above, so they are created in a second pass.
        for (final String pack : PACKS)
        {
            if (getBooleanSystemSetupParameter(context, "ccsampledata.pack." + pack))
            {
                loadOrders(IMPORT_ROOT + "orders/" + pack + ".json");
            }
        }
        if (any)
        {
            loadOrders(IMPORT_ROOT + "orders/all-packs.json");
        }
    }

    @SystemSetupParameterMethod
    @Override
    public List<SystemSetupParameter> getInitializationOptions()
    {
        final List<SystemSetupParameter> params = new ArrayList<>();
        for (final String pack : PACKS)
        {
            final SystemSetupParameter p = new SystemSetupParameter("ccsampledata.pack." + pack);
            p.setLabel("Load demo pack: " + pack + " (synthetic data only)");
            p.addValue("true", true);
            p.addValue("false", false);
            params.add(p);
        }
        return params;
    }

    private void loadOrders(final String path)
    {
        try
        {
            sampleOrderLoader.load(path, LocalDate.now(), configurationService.syntheticDataOnly());
        }
        catch (final IOException e)
        {
            LOG.warn("Sample orders could not be read: {}", path);
        }
    }
}
