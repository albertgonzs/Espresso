package io.github.marktony.espresso.mvp.packages;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomNavigationView;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import java.util.List;

import io.github.marktony.espresso.R;
import io.github.marktony.espresso.mvp.addpack.AddPackageActivity;
import io.github.marktony.espresso.data.Package;
import io.github.marktony.espresso.interfaze.OnRecyclerViewItemClickListener;
import io.github.marktony.espresso.mvp.packagedetails.PackageDetailsActivity;
import io.github.marktony.espresso.mvp.packagedetails.PackageDetailsFragment;

/**
 * Created by lizhaotailang on 2017/2/10.
 */

public class PackagesFragment extends Fragment
        implements PackagesContract.View {

    private BottomNavigationView bottomNavigationView;
    private FloatingActionButton fab;
    private RecyclerView recyclerView;
    private LinearLayout emptyView;
    private SwipeRefreshLayout refreshLayout;

    private PackageAdapter adapter;

    private PackagesContract.Presenter presenter;

    private String selectedPackageNumber;

    private LocalBroadcastManager manager;
    private LocalReceiver receiver;

    public PackagesFragment() {}

    public static final int REQUEST_OPEN_DETAILS = 0;

    public static PackagesFragment newInstance() {
        return new PackagesFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view =  inflater.inflate(R.layout.fragment_packages, container, false);

        initViews(view);

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(new Intent(getContext(), AddPackageActivity.class), AddPackageActivity.REQUEST_ADD_PACKAGE);
            }
        });

        bottomNavigationView.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                switch (item.getItemId()) {

                    case R.id.nav_all:
                        presenter.setFiltering(PackageFilterType.ALL_PACKAGES);
                        break;

                    case R.id.nav_on_the_way:
                        presenter.setFiltering(PackageFilterType.ON_THE_WAY_PACKAGES);
                        break;

                    case R.id.nav_delivered:
                        presenter.setFiltering(PackageFilterType.DELIVERED_PACKAGES);
                        break;

                }
                presenter.loadPackages(false);

                return true;
            }
        });

        refreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                presenter.loadPackages(true);
            }
        });

        // Register the local broadcast receiver
        receiver = new LocalReceiver();
        manager = LocalBroadcastManager.getInstance(getContext());
        IntentFilter filter = new IntentFilter();
        filter.addAction(LocalReceiver.PACKAGES_RECEIVER_ACTION);
        manager.registerReceiver(receiver, filter);

        setHasOptionsMenu(true);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        presenter.subscribe();
    }

    @Override
    public void onPause() {
        super.onPause();
        presenter.unsubscribe();
        setLoadingIndicator(false);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // DO NOT forget to unregister the broadcast receiver
        manager.unregisterReceiver(receiver);
    }


    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.packages_list, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_search) {

        } else if (id == R.id.action_mark_all_read) {
            presenter.markAllPacksRead();
        }
        return true;
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (item == null || selectedPackageNumber == null) {
            return false;
        }
        switch (item.getItemId()) {
            case R.id.action_set_read_unread:
                presenter.setPackageReadUnread(getSelectedPackageNumber());
                adapter.notifyDataSetChanged();
                break;
            case R.id.action_copy_code:
                copyPackageNumber();
                break;
            case R.id.action_share:
                presenter.setShareData(getSelectedPackageNumber());
                break;
            default:
                break;
        }
        return true;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_OPEN_DETAILS:
                Bundle bundle = data.getExtras();
                if (null != bundle) {
                    int position = bundle.getInt(PackageDetailsActivity.PACKAGE_POSITION, -1);
                    String number = bundle.getString(PackageDetailsActivity.PACKAGE_ID);
                    if (position != -1 && number != null) {
                        if (resultCode == PackageDetailsFragment.RESULT_DELETE) {
                            presenter.deletePackage(position);
                        }

                        if (resultCode == PackageDetailsFragment.RESULT_SET_UNREAD) {
                            presenter.setPackageReadUnread(number);
                        }
                    }

                }
                break;
            default:
                break;
        }
    }

    @Override
    public void initViews(View view) {

        fab = (FloatingActionButton) view.findViewById(R.id.fab);
        bottomNavigationView = (BottomNavigationView) view.findViewById(R.id.bottomNavigationView);
        emptyView = (LinearLayout) view.findViewById(R.id.emptyView);
        recyclerView = (RecyclerView) view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        refreshLayout = (SwipeRefreshLayout) view.findViewById(R.id.refreshLayout);
        refreshLayout.setColorSchemeColors(ContextCompat.getColor(getContext(), R.color.colorPrimary));

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                // returning false means that we need not to handle the drag action
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                presenter.deletePackage(viewHolder.getLayoutPosition());
            }

            @Override
            public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
                getDefaultUIUtil().clearView(((PackageAdapter.PackageViewHolder) viewHolder).layoutMain);
                ((PackageAdapter.PackageViewHolder) viewHolder).textViewRemove.setVisibility(View.GONE);
                ((PackageAdapter.PackageViewHolder) viewHolder).imageViewRemove.setVisibility(View.GONE);
            }

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
                if (viewHolder != null) {
                    getDefaultUIUtil().onSelected(((PackageAdapter.PackageViewHolder) viewHolder).layoutMain);
                }
            }

            @Override
            public void onChildDraw(Canvas c, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                getDefaultUIUtil().onDraw(c, recyclerView, ((PackageAdapter.PackageViewHolder) viewHolder).layoutMain, dX, dY, actionState, isCurrentlyActive);
                if (dX > 0) {
                    ((PackageAdapter.PackageViewHolder) viewHolder).wrapperView.setBackgroundResource(R.color.deep_orange);
                    ((PackageAdapter.PackageViewHolder) viewHolder).imageViewRemove.setVisibility(View.VISIBLE);
                    ((PackageAdapter.PackageViewHolder) viewHolder).textViewRemove.setVisibility(View.GONE);
                }

                if (dX < 0) {
                    ((PackageAdapter.PackageViewHolder) viewHolder).wrapperView.setBackgroundResource(R.color.deep_orange);
                    ((PackageAdapter.PackageViewHolder) viewHolder).imageViewRemove.setVisibility(View.GONE);
                    ((PackageAdapter.PackageViewHolder) viewHolder).textViewRemove.setVisibility(View.VISIBLE);
                }

            }

            @Override
            public void onChildDrawOver(Canvas c, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                getDefaultUIUtil().onDrawOver(c, recyclerView, ((PackageAdapter.PackageViewHolder) viewHolder).layoutMain, dX, dY, actionState, isCurrentlyActive);
            }
        });
        itemTouchHelper.attachToRecyclerView(recyclerView);
    }

    @Override
    public void setPresenter(@NonNull PackagesContract.Presenter presenter) {
        this.presenter = presenter;
    }

    @Override
    public void setLoadingIndicator(boolean active) {
        refreshLayout.setRefreshing(active);
    }

    @Override
    public void showEmptyView(boolean toShow) {
        if (toShow) {
            recyclerView.setVisibility(View.INVISIBLE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void showPackages(@NonNull final List<Package> list) {
        if (adapter == null) {
            adapter = new PackageAdapter(getContext(), list);
            adapter.setOnRecyclerViewItemClickListener(new OnRecyclerViewItemClickListener() {
                @Override
                public void OnItemClick(View v, int position) {
                    Intent intent = new Intent(getContext(), PackageDetailsActivity.class);
                    intent.putExtra(PackageDetailsActivity.PACKAGE_ID, list.get(position).getNumber());
                    intent.putExtra(PackageDetailsActivity.PACKAGE_POSITION, position);
                    startActivityForResult(intent, REQUEST_OPEN_DETAILS);
                }

            });
            recyclerView.setAdapter(adapter);
        } else {
            adapter.updateData(list);
        }
        showEmptyView(list.isEmpty());
    }

    @Override
    public void shareTo(@NonNull Package pack) {
        String shareData = pack.getName()
                + "\n( "
                + pack.getNumber()
                + " "
                + pack.getCompanyChineseName()
                + " )\n"
                + getString(R.string.latest_status);
        if (pack.getData() != null && !pack.getData().isEmpty()) {
            shareData = shareData
                    + pack.getData().get(0).getContext()
                    + pack.getData().get(0).getFtime();
        } else {
            shareData = shareData + getString(R.string.get_status_error);
        }
        try {
            Intent intent = new Intent().setAction(Intent.ACTION_SEND).setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, shareData);
            startActivity(Intent.createChooser(intent, getString(R.string.share)));

        } catch (ActivityNotFoundException e) {
            Snackbar.make(fab, R.string.something_wrong, Snackbar.LENGTH_SHORT).show();
        }
    }

    @Override
    public void showPackageRemovedMsg(String packageName) {
        String msg = packageName
                + " "
                + getString(R.string.package_removed_msg);
        Snackbar.make(fab, msg, Snackbar.LENGTH_LONG)
                .setAction(R.string.undo, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        presenter.recoverPackage();
                    }
                })
                .show();
    }

    @Override
    public void copyPackageNumber() {
        ClipboardManager manager = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData data = ClipData.newPlainText("text", getSelectedPackageNumber());
        manager.setPrimaryClip(data);
        Snackbar.make(fab, R.string.package_number_copied, Snackbar.LENGTH_SHORT).show();
    }

    public void setSelectedPackage(@NonNull String packId) {
        this.selectedPackageNumber = packId;
    }

    public String getSelectedPackageNumber() {
        return selectedPackageNumber;
    }

    /**
     * A local broadcast receiver. When receive the
     * broadcast, update the ui.
     */
    public class LocalReceiver extends BroadcastReceiver {

        public static final String PACKAGES_RECEIVER_ACTION
                = "io.github.marktony.espresso.PACKAGES_RECEIVER_ACTION";

        @Override
        public void onReceive(Context context, Intent intent) {
            setLoadingIndicator(false);
            if (intent.getBooleanExtra("result", false)) {
                adapter.notifyDataSetChanged();
            }
        }
    }

}

