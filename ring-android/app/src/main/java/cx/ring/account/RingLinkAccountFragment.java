/*
 *  Copyright (C) 2004-2016 Savoir-faire Linux Inc.
 *
 *  Author: Adrien Béraud <adrien.beraud@savoirfairelinux.com>
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
 *   Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

package cx.ring.account;

import android.os.Bundle;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnTextChanged;
import cx.ring.R;
import cx.ring.application.RingApplication;
import cx.ring.mvp.BaseFragment;
import cx.ring.mvp.RingAccountViewModel;

public class RingLinkAccountFragment extends BaseFragment<RingLinkAccountPresenter> implements RingLinkAccountView {

    public static final String TAG = RingLinkAccountFragment.class.getSimpleName();

    @BindView(R.id.ring_add_pin)
    protected EditText mPinTxt;

    @BindView(R.id.ring_existing_password)
    protected EditText mPasswordTxt;

    @BindView(R.id.link_button)
    protected Button mLinkAccountBtn;

    public static RingLinkAccountFragment newInstance(RingAccountViewModelImpl ringAccountViewModel) {
        Bundle bundle = new Bundle();
        bundle.putParcelable(RingAccountCreationFragment.KEY_RING_ACCOUNT, ringAccountViewModel);
        RingLinkAccountFragment fragment = new RingLinkAccountFragment();
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.frag_acc_ring_link, parent, false);

        ButterKnife.bind(this, view);

        // dependency injection
        ((RingApplication) getActivity().getApplication()).getRingInjectionComponent().inject(this);

        RingAccountViewModelImpl ringAccountViewModel = getArguments().getParcelable(RingAccountCreationFragment.KEY_RING_ACCOUNT);
        presenter.init(ringAccountViewModel);

        return view;
    }

    @OnClick(R.id.link_button)
    public void onLinkClick() {
        presenter.linkClicked();
    }

    @OnTextChanged(value = R.id.ring_existing_password, callback = OnTextChanged.Callback.AFTER_TEXT_CHANGED)
    public void afterPasswordChanged(Editable txt) {
        presenter.passwordChanged(txt.toString());
    }

    @OnTextChanged(value = R.id.ring_add_pin, callback = OnTextChanged.Callback.AFTER_TEXT_CHANGED)
    public void afterPinChanged(Editable txt) {
        presenter.pinChanged(txt.toString());
    }

    @Override
    public void enableLinkButton(boolean enable) {
        mLinkAccountBtn.setEnabled(enable);
    }

    @Override
    public void createAccount(RingAccountViewModel ringAccountViewModel) {
        ((AccountWizard) getActivity()).createAccount(ringAccountViewModel);
    }
}