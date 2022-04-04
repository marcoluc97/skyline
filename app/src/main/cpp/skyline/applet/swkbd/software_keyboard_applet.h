// SPDX-License-Identifier: MPL-2.0
// Copyright © 2022 Skyline Team and Contributors (https://github.com/skyline-emu/)

#include <services/am/applet/IApplet.h>
#include <services/applet/common_arguments.h>
#include <jni.h>
#include "software_keyboard_config.h"

namespace skyline::service::applet::swkbd {
    /**
     * @brief Return values for swkbd
     * @url https://switchbrew.org/wiki/Software_Keyboard#CloseResult
     */
    enum class CloseResult : u32 {
        Enter = 0x0,
        Cancel = 0x1,
    };
    /**
     * @url https://switchbrew.org/wiki/Software_Keyboard#TextCheckResult
     */
    enum class TextCheckResult : u32 {
        Success = 0x0,
        ShowFailureDialog = 0x1,
        ShowConfirmDialog = 0x2,
    };

    enum class ValidationErrorCode : u8 {
        InvalidChar = 0x0,
    };


    class SoftwareKeyboardApplet : public service::am::IApplet {
      private:
        std::queue<std::shared_ptr<service::am::IStorage>> normalInputData;
        std::queue<std::shared_ptr<service::am::IStorage>> interactiveInputData;
        CommonArguments common_args{};
        KeyboardConfigVB config{};
        bool verification_pending = false;
        std::u16string current_text;
        std::shared_ptr<service::am::IStorage> workBufferStorage;

        void PushUTF16TextForCheck(std::u16string_view text);
        void PushUTF16OutputText(std::u16string_view text);

        std::u16string GetKeyboardText(jobject dialog);
        jobject dialog = nullptr;

      public:
        SoftwareKeyboardApplet(const DeviceState &state, service::ServiceManager &manager, std::shared_ptr<kernel::type::KEvent> onAppletStateChanged, std::shared_ptr<kernel::type::KEvent> onNormalDataPushFromApplet, std::shared_ptr<kernel::type::KEvent> onInteractiveDataPushFromApplet, LibraryAppletMode appletMode);

        Result Start() override;

        Result GetResult() override;

        void PushNormalDataToApplet(std::shared_ptr<service::am::IStorage> data) override;

        void PushInteractiveDataToApplet(std::shared_ptr<service::am::IStorage> data) override;

    };
}