/*
 *  Copyright (C) 2004-2020 Savoir-faire Linux Inc.
 *
 *  Author: Alexandre Lision <alexandre.lision@savoirfairelinux.com>
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package cx.ring.tv.main;

import java.util.List;

import cx.ring.model.Account;
import cx.ring.navigation.HomeNavigationViewModel;
import cx.ring.smartlist.SmartListViewModel;
import cx.ring.model.Error;

public interface MainView {

    void showLoading(boolean show);

    void refreshContact(int index, SmartListViewModel contact);

    void showContacts(List<SmartListViewModel> contacts);

    void showContactRequests(List<SmartListViewModel> contactRequests);

    void callContact(String accountID, String ringID);

    void displayErrorToast(Error error);

    void displayAccountInfos(HomeNavigationViewModel viewModel);
    void updateModel(Account account);

    void showExportDialog(String pAccountID, boolean hasPassword);

    void showProfileEditing();

    void showAccountShare();

    void showLicence(int aboutType);

    void showSettings();
}
