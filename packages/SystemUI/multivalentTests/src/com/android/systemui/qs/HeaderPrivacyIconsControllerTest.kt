package com.android.systemui.qs

import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.logging.UiEventLogger
import com.android.systemui.SysuiTestCase
import com.android.systemui.privacy.OngoingPrivacyChip
import com.android.systemui.privacy.PrivacyDialogControllerV2
import com.android.systemui.privacy.PrivacyItemController
import com.android.systemui.privacy.logging.PrivacyLogger
import com.android.systemui.shade.data.repository.shadeDialogContextInteractor
import com.android.systemui.statusbar.phone.StatusIconContainer
import com.android.systemui.statusbar.policy.DeviceProvisionedController
import com.android.systemui.testKosmos
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.kotlin.never
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.eq
import org.mockito.kotlin.any
import org.mockito.MockitoAnnotations
import org.mockito.Mockito.`when` as whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class HeaderPrivacyIconsControllerTest : SysuiTestCase() {

    private val kosmos = testKosmos()
    @Mock
    private lateinit var privacyItemController: PrivacyItemController
    @Mock
    private lateinit var uiEventLogger: UiEventLogger
    @Mock
    private lateinit var privacyChip: OngoingPrivacyChip
    @Mock
    private lateinit var privacyDialogControllerV2: PrivacyDialogControllerV2
    @Mock
    private lateinit var privacyLogger: PrivacyLogger
    @Mock
    private lateinit var iconContainer: StatusIconContainer
    @Mock
    private lateinit var deviceProvisionedController: DeviceProvisionedController

    private lateinit var cameraSlotName: String
    private lateinit var microphoneSlotName: String
    private lateinit var locationSlotName: String
    private lateinit var controller: HeaderPrivacyIconsController

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        whenever(privacyChip.privacyList).thenReturn(emptyList())
        whenever(privacyChip.context).thenReturn(context)
        whenever(privacyChip.resources).thenReturn(context.resources)
        whenever(deviceProvisionedController.isDeviceProvisioned).thenReturn(true)

        cameraSlotName = context.getString(com.android.internal.R.string.status_bar_camera)
        microphoneSlotName = context.getString(com.android.internal.R.string.status_bar_microphone)
        locationSlotName = context.getString(com.android.internal.R.string.status_bar_location)

        controller = HeaderPrivacyIconsController(
                privacyItemController,
                uiEventLogger,
                privacyChip,
                privacyDialogControllerV2,
                privacyLogger,
                iconContainer,
                deviceProvisionedController,
                kosmos.shadeDialogContextInteractor,
        )
    }

    @Test
    fun locationOnlyClick_opensLocationCapableDialog() {
        whenever(privacyChip.privacyList).thenReturn(listOf(
            com.android.systemui.privacy.PrivacyItem(
                com.android.systemui.privacy.PrivacyType.TYPE_LOCATION,
                com.android.systemui.privacy.PrivacyApplication("test.location", 10001),
            )
        ))
        controller.onParentVisible()
        val captor = argumentCaptor<View.OnClickListener>()
        verify(privacyChip).setOnClickListener(captor.capture())
        captor.firstValue.onClick(privacyChip)
        verify(privacyDialogControllerV2).showDialog(any(), eq(privacyChip))
    }

    @Test
    fun mixedCameraLocationClick_keepsBothTypesInDialog() {
        whenever(privacyChip.privacyList).thenReturn(listOf(
            com.android.systemui.privacy.PrivacyItem(
                com.android.systemui.privacy.PrivacyType.TYPE_LOCATION,
                com.android.systemui.privacy.PrivacyApplication("test.location", 10001),
            ),
            com.android.systemui.privacy.PrivacyItem(
                com.android.systemui.privacy.PrivacyType.TYPE_CAMERA,
                com.android.systemui.privacy.PrivacyApplication("test.camera", 10002),
            ),
        ))
        controller.onParentVisible()
        val captor = argumentCaptor<View.OnClickListener>()
        verify(privacyChip).setOnClickListener(captor.capture())
        captor.firstValue.onClick(privacyChip)
        verify(privacyDialogControllerV2).showDialog(any(), eq(privacyChip))
    }

    @Test
    fun testIgnoredSlotsOnParentVisible_noIndicators() {
        setPrivacyController(micCamera = false, location = false)

        controller.onParentVisible()

        verify(iconContainer).removeIgnoredSlot(cameraSlotName)
        verify(iconContainer).removeIgnoredSlot(microphoneSlotName)
        verify(iconContainer).removeIgnoredSlot(locationSlotName)
    }

    @Test
    fun testIgnoredSlotsOnParentVisible_onlyMicCamera() {
        setPrivacyController(micCamera = true, location = false)

        controller.onParentVisible()

        verify(iconContainer).addIgnoredSlot(cameraSlotName)
        verify(iconContainer).addIgnoredSlot(microphoneSlotName)
        verify(iconContainer).removeIgnoredSlot(locationSlotName)
    }

    @Test
    fun testIgnoredSlotsOnParentVisible_onlyLocation() {
        setPrivacyController(micCamera = false, location = true)

        controller.onParentVisible()

        verify(iconContainer).removeIgnoredSlot(cameraSlotName)
        verify(iconContainer).removeIgnoredSlot(microphoneSlotName)
        verify(iconContainer).addIgnoredSlot(locationSlotName)
    }

    @Test
    fun testIgnoredSlotsOnParentVisible_locationMicCamera() {
        setPrivacyController(micCamera = true, location = true)

        controller.onParentVisible()

        verify(iconContainer).addIgnoredSlot(cameraSlotName)
        verify(iconContainer).addIgnoredSlot(microphoneSlotName)
        verify(iconContainer).addIgnoredSlot(locationSlotName)
    }

    @Test
    fun microphoneClick_usesAnimatedDialog() {
        whenever(privacyChip.privacyList).thenReturn(listOf(
            com.android.systemui.privacy.PrivacyItem(
                com.android.systemui.privacy.PrivacyType.TYPE_MICROPHONE,
                com.android.systemui.privacy.PrivacyApplication("test.microphone", 10001),
            )
        ))
        controller.onParentVisible()
        val captor = argumentCaptor<View.OnClickListener>()
        verify(privacyChip).setOnClickListener(captor.capture())
        captor.firstValue.onClick(privacyChip)
        verify(privacyDialogControllerV2).showDialog(any(), eq(privacyChip))
    }

    @Test
    fun testNoDialogWhenDeviceNotProvisioned() {
        whenever(deviceProvisionedController.isDeviceProvisioned).thenReturn(false)
        controller.onParentVisible()

        val captor = argumentCaptor<View.OnClickListener>()
        verify(privacyChip).setOnClickListener(captor.capture())

        captor.firstValue.onClick(privacyChip)
        verify(privacyDialogControllerV2, never()).showDialog(any(), any())
    }

    private fun setPrivacyController(micCamera: Boolean, location: Boolean) {
        whenever(privacyItemController.micCameraAvailable).thenReturn(micCamera)
        whenever(privacyItemController.locationAvailable).thenReturn(location)
    }

}
