package org.joget.marketplace;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.userview.model.UserviewBuilderPalette;
import org.joget.apps.userview.model.UserviewMenu;
import org.joget.commons.util.ResourceBundleUtil;
import org.joget.commons.util.SetupManager;
import org.joget.commons.util.StringUtil;
import org.joget.commons.util.TimeZoneUtil;
import org.joget.directory.dao.UserDao;
import org.joget.directory.model.User;
import org.joget.directory.model.service.DirectoryUtil;
import org.joget.directory.model.service.ExtDirectoryManager;
import org.joget.directory.model.service.UserSecurity;
import org.joget.plugin.base.PluginManager;
import org.joget.workflow.model.service.WorkflowUserManager;
import org.joget.workflow.util.WorkflowUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationContext;

public class UserProfileMFAMenu extends UserviewMenu{
    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getLabel() {
        return "User Profile MFA Menu";
    }

    @Override
    public String getIcon() {
        return "<i class=\"fas fa-user-edit\"></i>";
    }

    public String getName() {
        return "User Profile MFA Menu";
    }

    public String getVersion() {
        return "7.0.0";
    }

    public String getDescription() {
        return "User Profile MFA Userview Menu";
    }

    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClass().getName(), "/properties/UserProfileMFAMenu.json", null, true, "messages/UserProfileMFAMenu");
    }
    
    @Override
    public String getDecoratedMenu() {
        if ("true".equals(getRequestParameter("isPreview")) || "Yes".equals(getPropertyString("showInPopupDialog"))) {
            // sanitize label
            String label = getPropertyString("label");
            if (label != null) {
                label = StringUtil.stripHtmlRelaxed(label);
            }

            String menu = "<a onclick=\"menu_" + getPropertyString("id") + "_showDialog();return false;\" class=\"menu-link\"><span>" + label + "</span></a>";
            menu += "<script>\n";

            if ("Yes".equals(getPropertyString("showInPopupDialog"))) {
                String url = getUrl() + "?embed=true";

                menu += "var menu_" + getPropertyString("id") + "Dialog = new PopupDialog(\"" + url + "\",\"\");\n";
            }
            menu += "function menu_" + getPropertyString("id") + "_showDialog(){\n";
            if ("true".equals(getRequestParameter("isPreview"))) {
                menu += "alert('Feature disabled in Preview Mode.');\n";
            } else {
                menu += "menu_" + getPropertyString("id") + "Dialog.init();\n";
            }
            menu += "}\n</script>";
            return menu;
        }
        return null;
    }
    
    @Override
    public boolean isHomePageSupported() {
        return true;
    }
    
    @Override
    public String getRenderPage() {
        if ("true".equals(getRequestParameterString("isPreview"))) {
            setProperty("isPreview", "true");
        } else {
            if ("submit".equals(getRequestParameterString("action"))) {
                // only allow POST
                HttpServletRequest request = WorkflowUtil.getHttpServletRequest();
                if (request != null && !"POST".equalsIgnoreCase(request.getMethod())) {
                    PluginManager pluginManager = (PluginManager)AppUtil.getApplicationContext().getBean("pluginManager");
                    String content = pluginManager.getPluginFreeMarkerTemplate(new HashMap(), getClass().getName(), "/templates/unauthorized.ftl", null);
                    return content;
                }

                submitForm();
            } else {
                viewForm(null);
            }
        }
        Map model = new HashMap();
        model.put("request", getRequestParameters());
        model.put("element", this);
        
        PluginManager pluginManager = (PluginManager)AppUtil.getApplicationContext().getBean("pluginManager");
        String content = pluginManager.getPluginFreeMarkerTemplate(model, getClass().getName(), "/templates/userProfile.ftl", null);
        return content;
    }
    
    private void viewForm(User submittedData) {
        setProperty("headerTitle", getPropertyString("label"));
        setProperty("view", "formView");

        ExtDirectoryManager directoryManager = (ExtDirectoryManager) AppUtil.getApplicationContext().getBean("directoryManager");
        ApplicationContext ac = AppUtil.getApplicationContext();
        WorkflowUserManager workflowUserManager = (WorkflowUserManager) ac.getBean("workflowUserManager");
        User currentUser = null;
        currentUser = directoryManager.getUserByUsername(workflowUserManager.getCurrentUsername());
        setProperty("user", currentUser);
        setProperty("timezones", TimeZoneUtil.getList());
        
        SetupManager setupManager = (SetupManager) ac.getBean("setupManager");
        String enableUserLocale = setupManager.getSettingValue("enableUserLocale");
        Map<String, String> localeStringList = new TreeMap<String, String>();
        if(enableUserLocale != null && enableUserLocale.equalsIgnoreCase("true")) {
            String userLocale = setupManager.getSettingValue("userLocale");
            Collection<String> locales = new HashSet();
            locales.addAll(Arrays.asList(userLocale.split(",")));
            
            Locale[] localeList = Locale.getAvailableLocales();
            for (int x = 0; x < localeList.length; x++) {
                String code = localeList[x].toString();
                if (locales.contains(code)) {
                    localeStringList.put(code, code + " - " +localeList[x].getDisplayName());
                }
            }
        }
        
        UserSecurity us = DirectoryUtil.getUserSecurity();
        if (us != null) {
            setProperty("policies", us.passwordPolicies());
            setProperty("userProfileFooter", us.getUserProfileFooter(currentUser));
        }
        
        String url = getUrl() + "?action=submit";
        setProperty("actionUrl", url);
    }

    private void submitForm() {
        ApplicationContext ac = AppUtil.getApplicationContext();
        WorkflowUserManager workflowUserManager = (WorkflowUserManager) ac.getBean("workflowUserManager");
        ExtDirectoryManager directoryManager = (ExtDirectoryManager) AppUtil.getApplicationContext().getBean("directoryManager");
           
        Collection<String> errors = new ArrayList<String>();
        Collection<String> passwordErrors = new ArrayList<String>();
        
        boolean authenticated = false;
                try {
                    if (directoryManager.authenticate(workflowUserManager.getCurrentUsername(), getRequestParameterString("oldPassword"))) {
                        authenticated = true;
                    }
                } catch (Exception e) { }
            
        
        UserSecurity us = DirectoryUtil.getUserSecurity();
        User currentUser = null;
        currentUser = directoryManager.getUserByUsername(workflowUserManager.getCurrentUsername());
                
        if (!authenticated) {
            if (errors == null) {
                errors = new ArrayList<String>();
            }
            errors.add(ResourceBundleUtil.getMessage("console.directory.user.error.label.authenticationFailed"));
        } else {
            if (us != null) {
                
                //to check if user has email set
                errors = us.validateUserOnProfileUpdate(currentUser);
            }
        }
        
        setProperty("errors", errors);

        if (authenticated && (errors != null && errors.isEmpty())) {
            
            if (us != null) {
                us.updateUserProfilePostProcessing(currentUser);
            }

            setAlertMessage(getPropertyString("message"));
            setProperty("headerTitle", getPropertyString("label"));
            if (getPropertyString("redirectURL") != null && !getPropertyString("redirectURL").isEmpty()) {
                setProperty("view", "redirect");
                boolean redirectToParent = "Yes".equals(getPropertyString("showInPopupDialog"));
                setRedirectUrl(getPropertyString("redirectURL"), redirectToParent);
            } else {
                setProperty("saved", "true");
                viewForm(null);
            }
            
        } else {
            viewForm(currentUser);
        }
    }

    @Override
    public String getCategory() {
        return "Marketplace";
    }

}
