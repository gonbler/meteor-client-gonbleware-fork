/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client/).
 * Copyright (c) 2021 Meteor Development.
 */

package meteordevelopment.meteorclient.gui.screens;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.WAccount;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.systems.accounts.Account;
import meteordevelopment.meteorclient.systems.accounts.Accounts;
import meteordevelopment.meteorclient.systems.accounts.MicrosoftLogin;
import meteordevelopment.meteorclient.systems.accounts.types.MicrosoftAccount;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import org.jetbrains.annotations.Nullable;

import static meteordevelopment.meteorclient.utils.Utils.mc;

public class AccountsScreen extends WindowScreen {
    public AccountsScreen(GuiTheme theme) {
        super(theme, "Accounts");
    }

    @Override
    public void initWidgets() {
        // Accounts
        for (Account<?> account : Accounts.get()) {
            WAccount wAccount = add(theme.account(this, account)).expandX().widget();
            wAccount.refreshScreenAction = this::reload;
        }

        // Add account
        WHorizontalList l = add(theme.horizontalList()).expandX().widget();

        addButton(l, "Cracked", () -> mc.setScreen(new AddCrackedAccountScreen(theme, this)));
        addButton(l, "Premium", () -> mc.setScreen(new AddPremiumAccountScreen(theme, this)));
        addButton(l, "Microsoft", () -> {
            locked = true;

            MicrosoftLogin.getRefreshToken(refreshToken -> {
                locked = false;

                if (refreshToken != null) {
                    MicrosoftAccount account = new MicrosoftAccount(refreshToken);
                    addAccount(null, account);
                }
            });
        });
        addButton(l, "The Altening", () -> mc.setScreen(new AddAlteningAccountScreen(theme, this)));
    }

    private void addButton(WContainer c, String text, Runnable action) {
        WButton button = c.add(theme.button(text)).expandX().widget();
        button.action = action;
    }

    public static void addAccount(@Nullable AddAccountScreen screen, Account<?> account) {
        if (screen != null) {
            screen.add.set("...");
            screen.locked = true;
        }

        MeteorExecutor.execute(() -> {
            if (account.fetchInfo() && account.fetchHead()) {
                Accounts.get().add(account);

                if (screen != null) {
                    screen.locked = false;
                    screen.onClose();
                    screen.parent.reload();
                }

                return;
            }

            if (screen != null) {
                screen.add.set("Add");
                screen.locked = false;
            }
        });
    }
}
