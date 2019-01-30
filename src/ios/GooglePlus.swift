import UIKit
import GoogleSignIn
import GoogleAPIClientForREST
import GTMSessionFetcher

@objc(GooglePlus)
class GooglePlus: CDVPlugin, GIDSignInDelegate, GIDSignInUIDelegate {
    var commandCallback: String?
    var authorizer: GTMFetcherAuthorizationProtocol?
    
    /**** SignIn SDK ****/
    @objc(signIn:dismissViewController:)
    func sign(_ signIn: GIDSignIn!, dismiss viewController: UIViewController!) {
        self.viewController.dismiss(animated: true, completion: nil)
    }
    
    @objc(signIn:presentViewController:)
    func sign(_ signIn: GIDSignIn!, present viewController: UIViewController!) {
        self.viewController.present(viewController, animated: true, completion: nil)
    }
    
    
    func sign(_ signIn: GIDSignIn!, didDisconnectWith user: GIDGoogleUser!,
              withError error: Error!) {
        if let error = error {
            self.sendError("\(error.localizedDescription)")
        } else {
            self.sendError("User disconnected !")
        }
    }
    
    
    func sign(_ signIn: GIDSignIn!, didSignInFor user: GIDGoogleUser!,
              withError error: Error!) {
        if let error = error {
            self.sendError("\(error.localizedDescription)")
        } else {
            // Save the authorizer for calls to Google API.
            self.authorizer = user.authentication.fetcherAuthorizer()
            
            var message: Dictionary<String, Any> = [
                "expires": user.authentication.accessTokenExpirationDate.timeIntervalSince1970,
                "serverAuthCode": user.serverAuthCode,
                "displayName": user.profile.name,
                "givenName": user.profile.givenName,
                "familyName": user.profile.familyName,
                "email": user.profile.email
            ]
            
            // If have, add the user profile picture to the result.
            if (user.profile.hasImage) {
                message["imageUrl"] = user.profile.imageURL(withDimension: 120)?.absoluteString
            }
            
            self.send(message)
        }
    }
    /**** END ****/
    
    
    @objc(login:)
    func login (command: CDVInvokedUrlCommand) {
        self.getGIDSignInObject(command).signIn()
    }
    
    @objc(trySilentLogin:)
    func trySilentLogin (command: CDVInvokedUrlCommand) {
        self.getGIDSignInObject(command).signInSilently()
    }
    
    @objc(logout:)
    func logout (command: CDVInvokedUrlCommand) {
        GIDSignIn.sharedInstance().signOut()
        self.sendString("Logged out", command.callbackId)
    }
    
    @objc(disconnect:)
    func disconnect (command: CDVInvokedUrlCommand) {
        GIDSignIn.sharedInstance().disconnect()
        self.sendString("Disconnected", command.callbackId)
    }
    
    func getGIDSignInObject (_ command: CDVInvokedUrlCommand) -> GIDSignIn! {
        self.commandCallback = command.callbackId
        
        // Enabled GTMFetcher logs.
        GTMSessionFetcher.setLoggingEnabled(true)
        
        let options: [String: Any] = command.arguments.first as! [String: Any]
        
        let reversedClientId: String! = self.getReversedClientId()
        if (reversedClientId == nil) {
            self.sendError("Could not find REVERSED_CLIENT_ID url scheme in app .plist")
            return nil
        }
        
        let scopesString: String! = options["scopes"] as? String
        let serverClientId: String! = options["webClientId"] as? String
        let offline: Bool = options["offline"] as! Bool
        let accountName: String! = options["accountName"] as? String
        
        // Initialize sign-in
        let signInObj: GIDSignIn = GIDSignIn()
        signInObj.signOut()
        
        signInObj.delegate = self
        signInObj.uiDelegate = self
        
        signInObj.clientID = self.reverseUrlScheme(reversedClientId)
        
        // If webClientId is include and offline is true, set serverClientId for received serverAuthCode.
        if (serverClientId != nil && offline) {
            signInObj.serverClientID = serverClientId
        }
        
        if (scopesString != nil && !scopesString.isEmpty) {
            signInObj.scopes = scopesString.split(separator: " ").map(String.init)
        }
        
        if (accountName != nil) {
            signInObj.loginHint = accountName
        }
        
        return signInObj
    }
    
    func reverseUrlScheme (_ reversedClientId: String) -> String {
        var originalArray: Array = reversedClientId.split(separator: ".").map(String.init)
        originalArray.reverse()
        let reversedString: String = originalArray.joined(separator: ".")
        
        return reversedString
    }
    
    // Get the REVERSED_CLIENT_ID
    func getReversedClientId () -> String! {
        if let urlTypes: [Any] = Bundle.main.infoDictionary!["CFBundleURLTypes"] as? [Any] {
            for value in urlTypes {
                let dict: [String: Any] = value as! [String: Any]
                let urlName: String = dict["CFBundleURLName"] as! String
                if (urlName == "REVERSED_CLIENT_ID") {
                    if let urlSchemes: [String] = dict["CFBundleURLSchemes"] as? [String] {
                        return urlSchemes[0]
                    }
                }
            }
        }
        return nil
    }
    
    /**** Call to Gmail API ****/
    
    @objc(callGoogleApi:)
    func callGoogleApi (command: CDVInvokedUrlCommand) {
        
        let options: [String: Any] = command.arguments.first as! [String: Any]
        
        // Build the query.
        let query: GTLRQuery = self.buildQuery(options)
        
        // Build service.
        let service: GTLRService = self.buildService(options)
        
        service.executeQuery(query, completionHandler: {(ticket, object, error: Error!) -> Void in
            if let error = error {
                self.sendError("\(error.localizedDescription)", command.callbackId)
            } else {
                var body: String = ""

                if let result = object as? GTLRObject {
                    body = result.jsonString()
                } else {
                    self.sendError("The object returned need to be an GTLRObject !", command.callbackId)
                }

                if query.uploadParameters != nil {
                    if let loaderId = ticket.objectFetcher?.responseHeaders!["x-guploader-uploadid"] {
                        var result: Dictionary<String, Any> = ["loaderId": loaderId]

                        if (body.count > 0) {
                            result["body"] = body
                        }

                        self.send(result, command.callbackId)
                        return
                    }
                }
                
                self.sendString(body, command.callbackId)
            }
            
            return
        })
    }
    
    @objc(callBatchGoogleApi:)
    func callBatchGoogleApi (command: CDVInvokedUrlCommand) {
        let batchQuery: GTLRBatchQuery = GTLRBatchQuery()
        let batchRequest: [String: Any] = command.arguments.first as! [String: Any]
        let requests = batchRequest["requests"] as! [Any]
        
        // Build all queries.
        for request in requests {
            if let options = request as? [String: Any] {
                let query: GTLRQuery = self.buildQuery(options)
                batchQuery.addQuery(query)
            }
        }
        
        // Build service.
        let service: GTLRService = self.buildService(nil)
        
        service.executeQuery(batchQuery, completionHandler: {(ticket, objects, error: Error!) -> Void in
            if let error = error {
                print(error)
                self.sendError("\(error.localizedDescription)", command.callbackId)
            } else {
                // objects need to be an GTLRBatchResult.
                if let objects = objects as? GTLRBatchResult {
                    let successes = objects.successes!
                    var result: [Any] = []
                    
                    for request in successes {
                        result.append(request.value.json as Any)
                    }
                    
                    self.sendString(self.arrayToJsonString(result)!, command.callbackId)
                } else {
                    self.sendError("The object returned need to be an GTLRBatchResult !", command.callbackId)
                }
            }
        })
    }
    
    func buildQuery (_ options: [String: Any]) -> GTLRQuery {
        let urlParams = options["urlParams"] as! NSMutableDictionary
        let requestMethod = options["requestMethod"] as! String
        let requestUrl = options["requestUrl"] as! String
        
        let query: GTLRQuery = GTLRQuery.init(pathURITemplate: requestUrl, httpMethod: requestMethod, pathParameterNames: ["userId"])
        
        query.json = urlParams
        
        // Set body if passed
        if let body = options["body"] {
            // If the call is an upload set GTLRUploqdParameters.
            if let upload = options["upload"] as? Bool {
                if (upload == true) {
                    if let dataString = body as? String {
                        query.uploadParameters = GTLRUploadParameters.init(data: dataString.data(using: .utf8)!, mimeType: "message/rfc822")
                    } else {
                        let dataString: String = self.arrayToJsonString(body)!
                        
                        query.uploadParameters = GTLRUploadParameters.init(data: dataString.data(using: .utf8)!, mimeType: "message/rfc822")
                    }
                }
            } else {
                // Else build the body Object.
                query.bodyObject = GTLRObject.init(json: body as? [AnyHashable: Any])
            }
        }
        
        return query
    }
    
    func buildService (_ options: [String: Any]!) -> GTLRService {
        let service = GTLRService.init()
        
        service.rootURLString = "https://content.googleapis.com/"
        service.servicePath = "gmail/v1/users/"
        service.batchPath = "batch/gmail/v1"
        service.resumableUploadPath = "upload/"
        
        if (options != nil) {
            if let headers = options["headers"] as? [String : String]{
                service.additionalHTTPHeaders = headers
            }
        }
        
        // Set the authorizer for the current connected user.
        service.authorizer = self.authorizer
        
        return service
    }
    
    /**** End ****/
    
    func arrayToJsonString (_ object: Any) -> String? {
        guard let data = try? JSONSerialization.data(withJSONObject: object, options: []) else {
            return nil
        }
        
        return String(data: data, encoding: String.Encoding.utf8)
    }
    
    // Send result
    func send (_ message: Dictionary<String, Any>, _ commandCallback: String? = nil, _ status: CDVCommandStatus = CDVCommandStatus_OK) {
        let callbackId = commandCallback != nil ? commandCallback : self.commandCallback
        
        if callbackId != nil {
            self.commandCallback = nil
            let pluginResult = CDVPluginResult(
                status: status,
                messageAs: message
            )
            self.commandDelegate!.send(
                pluginResult,
                callbackId: callbackId
            )
        }
    }
    
    func sendString (_ message: String, _ commandCallback: String? = nil, _ status: CDVCommandStatus = CDVCommandStatus_OK) {
        let callbackId = commandCallback != nil ? commandCallback : self.commandCallback

        if callbackId != nil {
            self.commandCallback = nil
            let pluginResult = CDVPluginResult(
                status: status,
                messageAs: message
            )
            self.commandDelegate!.send(
                pluginResult,
                callbackId: callbackId
            )
        }
    }
    
    // Send error
    func sendError (_ message: String, _ commandCallback: String? = nil) {
        self.sendString(message, commandCallback, CDVCommandStatus_ERROR)
    }
}